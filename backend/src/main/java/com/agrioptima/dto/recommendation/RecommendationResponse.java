package com.agrioptima.dto.recommendation;

import com.agrioptima.dto.requirement.Amounts;
import com.agrioptima.engine.MicronutrientCheck;
import com.agrioptima.ml.MlContracts;
import com.agrioptima.service.recommendation.YieldFeatureMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A complete recommendation run (the React dashboard's main data contract).
 *
 * <p>Units: nutrients kg/ha of N, P2O5, K2O ({@code *KgHa}) or kg for the whole field ({@code *FieldKg}); product
 * quantities kg product/ha and kg per field; money INR/ha ({@code *PerHa}) and INR per field ({@code field*});
 * yield t/ha. Only optimizer plans that passed the backend's {@code FertilizerPlanVerifier} are listed, and the
 * reported nutrients, cost and mass are the verifier's recomputed values.
 */
public record RecommendationResponse(
        Long id,
        LocalDateTime createdAt,
        String status,
        boolean feasible,
        String infeasibilityReason,
        FieldInfo field,
        CodeName crop,
        Stage growthStage,
        Soil soil,
        Requirement requirement,
        List<Plan> plans,
        Selected selectedPlan,
        Scoring scoring,
        YieldPredictionInfo yieldPrediction,
        List<Shortfall> infeasibility,
        List<String> warnings,
        List<String> assumptions,
        KnowledgeBaseRef knowledgeBase,
        String modelVersion,
        String optimizerSolver,
        String disclaimer,
        MlContracts.WeatherInfo weather,
        IpnsAdvisory ipns,
        List<MicronutrientCheck.Assessment> micronutrients) {

    public record FieldInfo(Long id, String name, String farmName, String location, BigDecimal areaHa,
                            String soilType, String irrigationType, String season, LocalDate sowingDate,
                            String previousCrop, BigDecimal latitude, BigDecimal longitude) {
    }

    public record CodeName(String code, String name) {
    }

    public record Stage(String code, String name, int seq) {
    }

    public record Soil(boolean soilTestUsed, LocalDate sampleDate, Long ageDays, BigDecimal availableNKgHa,
                        BigDecimal availablePKgHa, BigDecimal availableKKgHa, BigDecimal ph,
                        BigDecimal organicCarbonPct, String nClass, String pClass, String kClass) {
    }

    /** {@code dueNowKgHa} is exactly what the optimizer received. */
    public record Requirement(Amounts dueNowKgHa, Amounts dueNowFieldKg, Amounts alreadyAppliedKgHa,
                              Amounts remainingSeasonKgHa, String profileCode, String profileName) {
    }

    public record Item(String code, String name, BigDecimal kgHa, BigDecimal fieldKg, BigDecimal bagKg,
                       BigDecimal fieldBags, BigDecimal costPerHa, BigDecimal fieldCost) {
    }

    public record PlanYield(boolean available, BigDecimal predictedYieldTHa, BigDecimal fieldProductionT,
                            boolean extrapolation, List<String> clippedFeatures, Amounts seasonNutrientsKgHa) {
    }

    public record PlanScore(BigDecimal expectedRevenuePerHa, BigDecimal fertilizerCostPerHa,
                            BigDecimal excessPenaltyPerHa, BigDecimal scorePerHa, int rank) {
    }

    public record Plan(String strategy, String label, String description, boolean selected, boolean feasible,
                       List<Item> items, Amounts suppliedKgHa, Amounts suppliedFieldKg, Amounts excessKgHa,
                       BigDecimal totalExcessKgHa, BigDecimal costPerHa, BigDecimal fieldCost,
                       BigDecimal totalMassKgHa, BigDecimal totalMassFieldKg, List<String> sameAs, PlanYield yield,
                       PlanScore score) {
    }

    public record Selected(String strategy, String label, String reason) {
    }

    public record Scoring(String mode, String formula, BigDecimal cropPriceInrPerTonne, String cropPriceSource,
                          BigDecimal excessPenaltyInrPerKg, String tieBreak) {
    }

    public record YieldPredictionInfo(boolean available, String unavailableReason, String modelVersion,
                                      List<YieldFeatureMapper.Input> inputs) {
    }

    public record Shortfall(String nutrient, BigDecimal requiredKgHa, BigDecimal maxSupplyKgHa, String reason) {
    }

    public record KnowledgeBaseRef(String id, String version, String status) {
    }

    public record IpnsAdvisory(BigDecimal chemicalNKgHa, BigDecimal organicNKgHa,
                               BigDecimal fymKgHa, BigDecimal vermicompostKgHa,
                               BigDecimal fymFieldKg, BigDecimal vermicompostFieldKg,
                               String note) {
    }

    public RecommendationResponse withIdentity(Long id, LocalDateTime createdAt) {
        return new RecommendationResponse(id, createdAt, status, feasible, infeasibilityReason, field, crop,
                growthStage, soil, requirement, plans, selectedPlan, scoring, yieldPrediction, infeasibility,
                warnings, assumptions, knowledgeBase, modelVersion, optimizerSolver, disclaimer,
                weather, ipns, micronutrients);
    }
}
