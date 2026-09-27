package com.agrioptima.service.recommendation;

import com.agrioptima.dto.recommendation.RecommendationResponse;
import com.agrioptima.dto.recommendation.RecommendationResponse.CodeName;
import com.agrioptima.dto.recommendation.RecommendationResponse.FieldInfo;
import com.agrioptima.dto.recommendation.RecommendationResponse.Item;
import com.agrioptima.dto.recommendation.RecommendationResponse.Plan;
import com.agrioptima.dto.recommendation.RecommendationResponse.PlanScore;
import com.agrioptima.dto.recommendation.RecommendationResponse.PlanYield;
import com.agrioptima.dto.recommendation.RecommendationResponse.Requirement;
import com.agrioptima.dto.recommendation.RecommendationResponse.Scoring;
import com.agrioptima.dto.recommendation.RecommendationResponse.Selected;
import com.agrioptima.dto.recommendation.RecommendationResponse.Shortfall;
import com.agrioptima.dto.recommendation.RecommendationResponse.YieldPredictionInfo;
import com.agrioptima.dto.recommendation.RecommendationSummary;
import com.agrioptima.dto.requirement.Amounts;
import com.agrioptima.dto.requirement.NutrientRequirementResponse;
import com.agrioptima.engine.NutrientAmounts;
import com.agrioptima.engine.plan.FertilizerPlanVerifier;
import com.agrioptima.engine.plan.FertilizerPlanVerifier.CandidatePlan;
import com.agrioptima.engine.plan.FertilizerPlanVerifier.ClaimedTotals;
import com.agrioptima.engine.plan.FertilizerPlanVerifier.PlanLine;
import com.agrioptima.engine.plan.FertilizerPlanVerifier.PlanVerification;
import com.agrioptima.engine.plan.FertilizerPlanVerifier.ProductSpec;
import com.agrioptima.entity.Fertilizer;
import com.agrioptima.entity.Field;
import com.agrioptima.entity.Recommendation;
import com.agrioptima.entity.RecommendationPlan;
import com.agrioptima.exception.ResourceNotFoundException;
import com.agrioptima.ml.MlContracts;
import com.agrioptima.ml.MlContracts.OptimizeResponse;
import com.agrioptima.ml.MlContracts.PredictResponse;
import com.agrioptima.ml.MlServiceClient;
import com.agrioptima.ml.MlServiceException;
import com.agrioptima.repository.FertilizerRepository;
import com.agrioptima.repository.FieldRepository;
import com.agrioptima.repository.RecommendationRepository;
import com.agrioptima.service.FieldService;
import com.agrioptima.service.NutrientRequirementService;
import com.agrioptima.service.recommendation.PlanScoringService.Candidate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * End-to-end recommendation run (Milestone 7):
 * <pre>
 * field (owner-checked) -> M4 NutrientRequirementService -> ML /optimize -> FertilizerPlanVerifier
 *   -> ML /predict-yield (one scenario per verified plan) -> PlanScoringService -> persist -> response
 * </pre>
 * The service orchestrates only: the requirement comes from the M4 engine, plans from the M5 optimizer (unchanged),
 * yields from the M3 model. No database transaction is held open during the HTTP calls.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final Map<String, String[]> STRATEGIES = Map.of(
            "LOWEST_COST", new String[]{"Lowest cost", "Cheapest combination that meets the requirement."},
            "MIN_EXCESS", new String[]{"Minimum excess", "Closest nutrient match: least N+P2O5+K2O beyond the requirement."},
            "BALANCED", new String[]{"Balanced", "Least excess while spending at most half of the extra cost of the minimum-excess plan."});
    private static final Set<String> STATUSES = Set.of("OPTIMAL", "NOTHING_REQUIRED", "INFEASIBLE");

    private final NutrientRequirementService requirementService;
    private final FieldService fieldService;
    private final FieldRepository fieldRepository;
    private final FertilizerRepository fertilizerRepository;
    private final RecommendationRepository recommendationRepository;
    private final MlServiceClient ml;
    private final PlanScoringService scoring;
    private final RecommendationProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public RecommendationService(NutrientRequirementService requirementService, FieldService fieldService,
                                 FieldRepository fieldRepository, FertilizerRepository fertilizerRepository,
                                 RecommendationRepository recommendationRepository, MlServiceClient ml,
                                 PlanScoringService scoring, RecommendationProperties properties,
                                 ObjectMapper objectMapper, PlatformTransactionManager txManager) {
        this.requirementService = requirementService;
        this.fieldService = fieldService;
        this.fieldRepository = fieldRepository;
        this.fertilizerRepository = fertilizerRepository;
        this.recommendationRepository = recommendationRepository;
        this.ml = ml;
        this.scoring = scoring;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
    }

    /** Field data needed after the read transaction (lazy associations resolved). */
    record Snapshot(FieldInfo field, CodeName crop, List<Fertilizer> fertilizers, YieldFeatureMapper.Base features) {
    }

    public RecommendationResponse create(Long ownerId, Long fieldId, String profileCode) {
        // 1-3: ownership (404), crop/stage present (400), requirement from the M4 engine
        NutrientRequirementResponse req = requirementService.forField(ownerId, fieldId, profileCode);
        Snapshot snap = readTx.execute(s -> snapshot(ownerId, fieldId));

        double area = req.areaHa().doubleValue();
        NutrientAmounts required = amounts(req.requirementForOptimizer().kgPerHa());
        List<ProductSpec> catalogue = snap.fertilizers().stream().map(f -> new ProductSpec(f.getCode(),
                f.getNPct().doubleValue(), f.getP2o5Pct().doubleValue(), f.getK2oPct().doubleValue(),
                f.getPricePerKg().doubleValue(), properties.maxKgHaPerProduct())).toList();

        // 4: optimizer (Milestone 5, via the ML service)
        OptimizeResponse opt = ml.optimize(new MlContracts.OptimizeRequest(
                new MlContracts.Nutrients(required.n(), required.p2o5(), required.k2o()), area,
                snap.fertilizers().stream().map(f -> new MlContracts.FertilizerOption(f.getCode(), f.getName(),
                        f.getNPct().doubleValue(), f.getP2o5Pct().doubleValue(), f.getK2oPct().doubleValue(),
                        f.getPricePerKg().doubleValue(), properties.maxKgHaPerProduct())).toList()));
        checkEcho(opt, required, area);

        List<String> warnings = new ArrayList<>(req.warnings());
        List<String> assumptions = new ArrayList<>(req.assumptions());
        assumptions.add("Per-product upper bound sent to the optimizer: " + fmt(properties.maxKgHaPerProduct())
                + " kg/ha (prototype tuning parameter).");
        if (opt.warnings() != null) {
            warnings.addAll(opt.warnings());
        }

        // 5: infeasible -> no yield prediction, no plans
        if ("INFEASIBLE".equals(opt.status())) {
            List<Shortfall> shortfalls = opt.infeasibility() == null ? List.of() : opt.infeasibility().stream()
                    .map(s -> new Shortfall(s.nutrient(), Amounts.round(s.requiredKgHa()),
                            Amounts.round(s.maxSupplyKgHa()), s.reason())).toList();
            String reason = opt.infeasibilityReason() != null ? opt.infeasibilityReason()
                    : "No plan can meet the requirement with the available fertilizers.";
            RecommendationResponse response = response(req, snap, opt, "INFEASIBLE", false, reason, List.of(), null,
                    null, new YieldPredictionInfo(false, "No yield prediction: no feasible plan.", null,
                            snap.features().inputs()), shortfalls, warnings, assumptions, null);
            return persist(fieldId, response, null, List.of());
        }

        // 6a: re-verify every plan in Java; keep only the valid ones
        List<Verified> verified = new ArrayList<>();
        for (MlContracts.Plan plan : opt.plans()) {
            PlanVerification v = FertilizerPlanVerifier.verify(required, area, catalogue, candidate(plan));
            if (v.valid()) {
                verified.add(new Verified(plan, v));
            } else {
                log.warn("dropping {} plan for field {}: {}", plan.strategy(), fieldId, v.violations());
                warnings.add("The " + plan.strategy() + " plan from the optimizer failed the backend re-check and was "
                        + "discarded.");
            }
        }
        if (verified.isEmpty()) {
            throw new MlServiceException(MlServiceException.Kind.INVALID_RESPONSE, "optimization", 200, null,
                    "no optimizer plan passed FertilizerPlanVerifier", null);
        }

        // 6b: yield prediction, one scenario per verified plan (season totals)
        NutrientAmounts applied = amounts(req.previousApplications().appliedKgHa());
        NutrientAmounts later = amounts(req.remainingSeasonKgHa()).minus(required).clampToZero();
        List<NutrientAmounts> seasonTotals = verified.stream()
                .map(v -> applied.plus(v.check().suppliedKgHa()).plus(later)).toList();
        YieldResult yields = predict(snap.features(), seasonTotals, warnings);
        assumptions.add("Yield scenarios use season totals: nutrients already applied this season + the plan + later "
                + "splits of the schedule (assumed applied as planned), because the model was trained on season totals.");

        // 6c: score and select (only among verified optimizer plans)
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < verified.size(); i++) {
            PlanVerification v = verified.get(i).check();
            candidates.add(new Candidate(verified.get(i).plan().strategy(), v.costPerHa(), total(v.excessKgHa()),
                    v.totalMassKgHa(), yields.yields() == null ? null : yields.yields().get(i).predictedYieldTHa()));
        }
        BigDecimal price = properties.cropPriceInrPerTonne().get(snap.crop().code());
        PlanScoringService.Result ranking = scoring.score(candidates, price, properties.excessPenaltyInrPerKg());
        assumptions.add("Plan score: " + ranking.formula() + ". Crop price " + (price == null ? "not configured"
                : price.toPlainString() + " INR/t (" + properties.cropPriceSource() + ")") + "; excess penalty "
                + fmt(properties.excessPenaltyInrPerKg()) + " INR per kg of excess nutrient (prototype weight). "
                + "The score only chooses among the optimizer's verified plans and never changes a plan.");

        List<Plan> plans = new ArrayList<>();
        Map<String, PlanScoringService.Score> byStrategy = new LinkedHashMap<>();
        ranking.scores().forEach(s -> byStrategy.put(s.strategy(), s));
        for (int i = 0; i < verified.size(); i++) {
            plans.add(plan(verified.get(i), byStrategy.get(verified.get(i).plan().strategy()),
                    yields.yields() == null ? null : yields.yields().get(i), seasonTotals.get(i), area,
                    ranking.selectedStrategy(), snap.fertilizers()));
        }
        String selected = ranking.selectedStrategy();
        Selected selectedPlan = new Selected(selected, STRATEGIES.get(selected)[0], ranking.selectionReason());
        Scoring scoringInfo = new Scoring(ranking.mode().name(), ranking.formula(), price, properties.cropPriceSource(),
                BigDecimal.valueOf(properties.excessPenaltyInrPerKg()), PlanScoringService.TIE_BREAK);
        YieldPredictionInfo yieldInfo = new YieldPredictionInfo(yields.yields() != null, yields.unavailableReason(),
                yields.modelVersion(), snap.features().inputs());

        RecommendationResponse response = response(req, snap, opt, opt.status(), true, null, plans, selectedPlan,
                scoringInfo, yieldInfo, List.of(), warnings, assumptions, yields.modelVersion());
        return persist(fieldId, response, ranking.mode().name(), plans);
    }

    public List<RecommendationSummary> history(Long ownerId, Long fieldId) {
        return readTx.execute(s -> {
            fieldService.requireOwned(ownerId, fieldId);
            return recommendationRepository.findAllByFieldIdOrderByCreatedAtDescIdDesc(fieldId).stream()
                    .map(r -> new RecommendationSummary(r.getId(), r.getCreatedAt(), r.getStatus(), r.isFeasible(),
                            r.getCropCode(), r.getStageCode(), r.getSelectedStrategy(), r.getScoringMode(),
                            r.getModelVersion()))
                    .toList();
        });
    }

    public RecommendationResponse get(Long ownerId, Long recommendationId) {
        return readTx.execute(s -> {
            Recommendation r = recommendationRepository.findOwned(recommendationId, ownerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Recommendation", recommendationId));
            try {
                return objectMapper.readValue(r.getResponseJson(), RecommendationResponse.class)
                        .withIdentity(r.getId(), asStored(r.getCreatedAt()));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("stored recommendation " + recommendationId + " is unreadable", e);
            }
        });
    }

    // --- steps ---------------------------------------------------------------------------------------------------

    private Snapshot snapshot(Long ownerId, Long fieldId) {
        Field f = fieldService.requireOwned(ownerId, fieldId);
        FieldInfo info = new FieldInfo(f.getId(), f.getName(), f.getFarm().getName(), f.getFarm().getLocationName(),
                f.getAreaHa(), f.getSoilType(), f.getIrrigationType() == null ? null : f.getIrrigationType().name(),
                f.getSeason() == null ? null : f.getSeason().name(), f.getSowingDate(), f.getPreviousCrop());
        CodeName crop = new CodeName(f.getCrop().getCode(), f.getCrop().getName());
        YieldFeatureMapper.Base features = YieldFeatureMapper.map(crop.code(), f.getFarm().getLocationName(),
                f.getSowingDate(), f.getSoilType(), f.getIrrigationType(), f.getPreviousCrop());
        return new Snapshot(info, crop, fertilizerRepository.findAllByActiveTrueOrderByNameAsc(), features);
    }

    private record Verified(MlContracts.Plan plan, PlanVerification check) {
    }

    private record YieldResult(List<MlContracts.Prediction> yields, String modelVersion, String unavailableReason) {
    }

    private YieldResult predict(YieldFeatureMapper.Base features, List<NutrientAmounts> seasonTotals,
                                List<String> warnings) {
        if (!features.available()) {
            warnings.add("No yield prediction: " + features.unavailableReason() + ". Plans are ranked on cost and "
                    + "excess only.");
            return new YieldResult(null, null, features.unavailableReason());
        }
        try {
            PredictResponse r = ml.predictYield(new MlContracts.PredictRequest(seasonTotals.stream()
                    .map(t -> features.scenario(t.n(), t.p2o5(), t.k2o())).toList()));
            List<MlContracts.Prediction> ordered = new ArrayList<>(r.predictions());
            ordered.sort((a, b) -> Integer.compare(a.index(), b.index()));
            if (r.warnings() != null) {
                r.warnings().forEach(w -> warnings.add("Yield model: " + w));
            }
            return new YieldResult(ordered, r.modelVersion(), null);
        } catch (MlServiceException e) {
            String reason = switch (e.upstreamProblemType() == null ? "" : e.upstreamProblemType()) {
                case "unsupported-crop" -> "the yield model does not support " + features.crop()
                        + " (it was trained on wheat and rice survey data only)";
                case "model-unavailable" -> "the yield model is not loaded in the ML service";
                default -> e.getMessage();
            };
            warnings.add("No yield prediction: " + reason + ". Plans are ranked on cost and excess only.");
            return new YieldResult(null, null, reason);
        }
    }

    private Plan plan(Verified v, PlanScoringService.Score score, MlContracts.Prediction prediction,
                      NutrientAmounts seasonTotal, double area, String selected, List<Fertilizer> fertilizers) {
        MlContracts.Plan p = v.plan();
        PlanVerification c = v.check();
        Map<String, Fertilizer> byCode = new LinkedHashMap<>();
        fertilizers.forEach(f -> byCode.put(f.getCode(), f));
        List<Item> items = p.items().stream().map(i -> {
            Fertilizer f = byCode.get(i.code());
            double price = f.getPricePerKg().doubleValue();
            BigDecimal bag = f.getBagKg();
            BigDecimal bags = bag == null || bag.signum() == 0 ? null
                    : BigDecimal.valueOf(i.kgHa() * area / bag.doubleValue()).setScale(1, RoundingMode.HALF_UP);
            return new Item(i.code(), f.getName(), kg(i.kgHa()), kg(i.kgHa() * area), bag, bags,
                    Amounts.round(i.kgHa() * price), Amounts.round(i.kgHa() * price * area));
        }).toList();
        PlanYield yield = prediction == null
                ? new PlanYield(false, null, null, false, List.of(), Amounts.of(seasonTotal))
                : new PlanYield(true, BigDecimal.valueOf(prediction.predictedYieldTHa()).setScale(3, RoundingMode.HALF_UP),
                BigDecimal.valueOf(prediction.predictedYieldTHa() * area).setScale(3, RoundingMode.HALF_UP),
                prediction.extrapolation(), prediction.clippedFeatures(), Amounts.of(seasonTotal));
        PlanScore planScore = new PlanScore(Amounts.round(score.expectedRevenuePerHa()),
                Amounts.round(score.fertilizerCostPerHa()), Amounts.round(score.excessPenaltyPerHa()),
                Amounts.round(score.scorePerHa()), score.rank());
        String[] label = STRATEGIES.getOrDefault(p.strategy(), new String[]{p.strategy(), ""});
        return new Plan(p.strategy(), label[0], label[1], p.strategy().equals(selected), c.valid(), items,
                Amounts.of(c.suppliedKgHa()), Amounts.of(c.suppliedFieldKg()), Amounts.of(c.excessKgHa()),
                kg(total(c.excessKgHa())), Amounts.round(c.costPerHa()), Amounts.round(c.fieldCost()),
                kg(c.totalMassKgHa()), kg(c.totalMassFieldKg()), p.sameAs() == null ? List.of() : p.sameAs(),
                yield, planScore);
    }

    private RecommendationResponse response(NutrientRequirementResponse req, Snapshot snap, OptimizeResponse opt,
                                            String status, boolean feasible, String infeasibilityReason,
                                            List<Plan> plans, Selected selected, Scoring scoringInfo,
                                            YieldPredictionInfo yieldInfo, List<Shortfall> shortfalls,
                                            List<String> warnings, List<String> assumptions, String modelVersion) {
        var s = req.soil();
        var lines = req.nutrients();
        var soil = new RecommendationResponse.Soil(s.soilTestUsed(), s.sampleDate(), s.ageDays(), s.availableN(),
                s.availableP(), s.availableK(), s.ph(), s.organicCarbonPct(), lines.get(0).soilClass().name(),
                lines.get(1).soilClass().name(), lines.get(2).soilClass().name());
        var requirement = new Requirement(req.requirementForOptimizer().kgPerHa(), req.requirementForOptimizer().fieldKg(),
                req.previousApplications().appliedKgHa(), req.remainingSeasonKgHa(), req.profile().code(),
                req.profile().description());
        var kb = new RecommendationResponse.KnowledgeBaseRef(req.knowledgeBase().id(), req.knowledgeBase().version(),
                req.knowledgeBase().status());
        String solver = opt.solver() == null ? null : opt.solver().library() + " (" + opt.solver().method()
                + ", SciPy " + opt.solver().scipyVersion() + ")";
        return new RecommendationResponse(null, null, status, feasible, infeasibilityReason, snap.field(), snap.crop(),
                new RecommendationResponse.Stage(req.stage().code(), req.stage().name(), req.stage().seq()), soil,
                requirement, plans, selected, scoringInfo, yieldInfo, shortfalls, List.copyOf(warnings),
                List.copyOf(assumptions), kb, modelVersion, solver, req.disclaimer());
    }

    private RecommendationResponse persist(Long fieldId, RecommendationResponse response, String scoringMode,
                                           List<Plan> plans) {
        String json;
        try {
            json = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialise recommendation", e);
        }
        Amounts due = response.requirement().dueNowKgHa();
        return writeTx.execute(s -> {
            Recommendation entity = new Recommendation(fieldRepository.getReferenceById(fieldId), response.status(),
                    response.feasible(), response.crop().code(), response.growthStage().code(),
                    response.field().areaHa(), due.n(), due.p2o5(), due.k2o(),
                    response.selectedPlan() == null ? null : response.selectedPlan().strategy(),
                    scoringMode == null ? "NONE" : scoringMode, response.knowledgeBase().version(),
                    response.modelVersion(), json);
            for (Plan p : plans) {
                entity.addPlan(new RecommendationPlan(entity, p.strategy(), p.costPerHa(), p.fieldCost(),
                        p.totalExcessKgHa(), p.totalMassKgHa(), p.yield().predictedYieldTHa(), p.score().scorePerHa(),
                        p.selected()));
            }
            Recommendation saved = recommendationRepository.saveAndFlush(entity);
            // the column is DATETIME(6): return the timestamp exactly as it will be read back
            return response.withIdentity(saved.getId(), asStored(saved.getCreatedAt()));
        });
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    private static void checkEcho(OptimizeResponse opt, NutrientAmounts required, double area) {
        MlContracts.Nutrients echo = opt.requirementKgHa();
        boolean same = echo.n() == required.n() && echo.p2o5() == required.p2o5() && echo.k2o() == required.k2o()
                && opt.areaHa() == area;
        if (!same || !STATUSES.contains(opt.status())) {
            throw new MlServiceException(MlServiceException.Kind.INVALID_RESPONSE, "optimization", 200, null,
                    "optimizer answered for a different requirement/area or with unknown status " + opt.status(), null);
        }
    }

    private static CandidatePlan candidate(MlContracts.Plan p) {
        List<PlanLine> lines = p.items().stream().map(i -> new PlanLine(i.code(), i.kgHa(), i.fieldKg())).toList();
        ClaimedTotals claimed = new ClaimedTotals(nutrients(p.suppliedKgHa()), nutrients(p.suppliedFieldKg()),
                nutrients(p.excessKgHa()), nutrients(p.excessFieldKg()), p.costPerHa(), p.fieldCost(),
                p.totalMassKgHa(), p.totalMassFieldKg(), p.feasible());
        return new CandidatePlan(p.strategy(), lines, claimed);
    }

    private static NutrientAmounts nutrients(MlContracts.Nutrients n) {
        return new NutrientAmounts(n.n(), n.p2o5(), n.k2o());
    }

    private static NutrientAmounts amounts(Amounts a) {
        return new NutrientAmounts(a.n().doubleValue(), a.p2o5().doubleValue(), a.k2o().doubleValue());
    }

    private static double total(NutrientAmounts a) {
        return a.n() + a.p2o5() + a.k2o();
    }

    private static BigDecimal kg(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP);
    }

    /** DATETIME(6) rounds to microseconds (H2 and MySQL round half up). */
    private static LocalDateTime asStored(LocalDateTime t) {
        return t.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
    }

    private static String fmt(double v) {
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }
}
