package com.agrioptima.engine;

import com.agrioptima.engine.NutrientRequirement.NutrientLine;
import com.agrioptima.engine.NutrientRequirement.PreviousUsage;
import com.agrioptima.engine.NutrientRequirement.ProfileInfo;
import com.agrioptima.engine.NutrientRequirement.ScheduleEntry;
import com.agrioptima.engine.NutrientRequirement.SoilAssessment;
import com.agrioptima.engine.NutrientRequirement.StagePosition;
import com.agrioptima.engine.RequirementInput.AppliedFertilizer;
import com.agrioptima.engine.RequirementInput.SoilTestInput;
import com.agrioptima.engine.RequirementInput.StageRef;
import com.agrioptima.engine.knowledge.KnowledgeBase;
import com.agrioptima.engine.knowledge.KnowledgeBase.CropKnowledge;
import com.agrioptima.engine.knowledge.KnowledgeBase.Profile;
import com.agrioptima.engine.knowledge.KnowledgeBase.Rating;
import com.agrioptima.engine.knowledge.KnowledgeBase.Schedule;
import com.agrioptima.engine.knowledge.KnowledgeBase.Split;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic nutrient-requirement calculation (architecture §6-7, "agricultural rules -> nutrient requirement").
 * <ol>
 *   <li>Pick the recommendation profile (explicit, rainfed, late-sown or crop default) -> general dose, kg/ha.</li>
 *   <li>Rate the soil test (LOW / MEDIUM / HIGH per nutrient) and scale the dose by the class factor.</li>
 *   <li>Sum the fertilizer already applied to this crop (season window) and convert it to kg/ha.</li>
 *   <li>Due now = adjusted dose x share scheduled up to the current stage - already applied (never negative).</li>
 * </ol>
 * No machine learning, no optimisation, no I/O: the same input always gives the same output.
 */
public class NutrientRequirementEngine {

    private final KnowledgeBase kb;

    public NutrientRequirementEngine(KnowledgeBase kb) {
        this.kb = kb;
    }

    public KnowledgeBase knowledgeBase() {
        return kb;
    }

    public NutrientRequirement calculate(RequirementInput in) {
        validate(in);
        CropKnowledge crop = kb.crop(in.cropCode())
                .orElseThrow(() -> new EngineInputException("No nutrient knowledge for crop " + in.cropCode() + "."));
        List<StageRef> stages = in.cropStages().stream().sorted(Comparator.comparingInt(StageRef::seq)).toList();
        StageRef current = stages.stream().filter(s -> s.code().equals(in.stageCode())).findFirst()
                .orElseThrow(() -> new EngineInputException(
                        "Growth stage " + in.stageCode() + " is not a stage of crop " + in.cropCode() + "."));

        List<String> warnings = new ArrayList<>();
        List<String> assumptions = new ArrayList<>();

        String[] reason = new String[1];
        Profile profile = selectProfile(crop, in, reason);
        Schedule schedule = crop.schedule(profile.schedule()).orElseThrow();
        ProfileInfo profileInfo = new ProfileInfo(profile.code(), profile.description(), profile.status(),
                profile.sourceId(), kb.source(profile.sourceId()).map(KnowledgeBase.Source::citation).orElse(null),
                reason[0]);

        // --- soil test -> fertility class -> adjustment factor
        Map<Nutrient, SoilFertilityClass> classes = new EnumMap<>(Nutrient.class);
        SoilAssessment soil = assessSoil(in, classes, warnings);

        // --- previous applications in this crop's season window
        PreviousUsage previous = previousUsage(in, warnings);

        // --- schedule: share due up to and including the current stage
        Map<String, StageRef> stageByCode = new java.util.HashMap<>();
        stages.forEach(s -> stageByCode.put(s.code(), s));
        NutrientAmounts cumulativeShare = NutrientAmounts.ZERO;
        Map<Nutrient, StageRef> lastScheduledStage = new EnumMap<>(Nutrient.class);
        for (Split split : schedule.splits()) {
            StageRef st = stageByCode.get(split.stage());
            if (st == null) {
                throw new IllegalStateException("Knowledge base schedule " + schedule.code() + " uses stage "
                        + split.stage() + " which is not a stage of " + in.cropCode());
            }
            if (st.seq() <= current.seq()) {
                cumulativeShare = cumulativeShare.plus(split.fractions());
            }
            for (Nutrient n : Nutrient.values()) {
                if (split.fraction(n).numerator() > 0
                        && (lastScheduledStage.get(n) == null || lastScheduledStage.get(n).seq() < st.seq())) {
                    lastScheduledStage.put(n, st);
                }
            }
        }

        // --- per-nutrient requirement
        NutrientAmounts general = profile.target().toAmounts();
        List<NutrientLine> lines = new ArrayList<>();
        double[] adjusted = new double[3];
        double[] due = new double[3];
        double[] remaining = new double[3];
        for (Nutrient n : Nutrient.values()) {
            SoilFertilityClass cls = classes.get(n);
            double factor = factor(cls);
            double adj = general.get(n) * factor;
            double applied = previous.appliedKgHa().get(n);
            double share = cumulativeShare.get(n);
            double rem = Math.max(0, adj - applied);
            double dueNow = Math.max(0, adj * share - applied);
            double excess = Math.max(0, applied - adj);
            adjusted[n.ordinal()] = adj;
            due[n.ordinal()] = dueNow;
            remaining[n.ordinal()] = rem;
            lines.add(new NutrientLine(n, general.get(n), cls, factor, adj, share, applied, rem, dueNow, excess,
                    dueNow * in.areaHa(), rem * in.areaHa()));

            if (excess > 0.005) {
                warnings.add(String.format(Locale.ROOT, "%s already applied this season (%.1f kg/ha) exceeds the "
                        + "adjusted season target (%.1f kg/ha) by %.1f kg/ha. No further %s is recommended.",
                        n, applied, adj, excess, n));
            }
            StageRef last = lastScheduledStage.get(n);
            if (dueNow > 0.005 && last != null && current.seq() > last.seq()) {
                warnings.add(String.format(Locale.ROOT, "%.1f kg/ha of %s scheduled for earlier stages (last: %s) "
                        + "was not applied. Its scheduled window has passed; applying it now may be ineffective. "
                        + "Check with a local agronomist before applying.", dueNow, n, last.code()));
            }
        }
        NutrientAmounts dueNowKgHa = new NutrientAmounts(due[0], due[1], due[2]);
        NutrientAmounts adjustedTarget = new NutrientAmounts(adjusted[0], adjusted[1], adjusted[2]);

        List<ScheduleEntry> scheduleEntries = new ArrayList<>();
        for (Split split : schedule.splits()) {
            StageRef st = stageByCode.get(split.stage());
            StagePosition pos = st.seq() < current.seq() ? StagePosition.PAST
                    : st.seq() == current.seq() ? StagePosition.CURRENT : StagePosition.UPCOMING;
            scheduleEntries.add(new ScheduleEntry(st.code(), st.name(), st.seq(), pos, split.fractions(),
                    adjustedTarget.times(split.fractions()), split.timing(), split.status()));
        }
        scheduleEntries.sort(Comparator.comparingInt(ScheduleEntry::seq));
        if (cumulativeShare.n() == 0 && cumulativeShare.p2o5() == 0 && cumulativeShare.k2o() == 0) {
            warnings.add("No fertilizer is scheduled up to the current stage " + current.code() + ".");
        }

        assumptions.add("General dose from profile " + profile.code() + " [" + profile.status() + ", source "
                + profile.sourceId() + "]. Blanket recommendation, not a site-specific prescription.");
        assumptions.add("Soil-test adjustment LOW x" + kb.soilTest().adjustmentFactors().low() + ", MEDIUM x"
                + kb.soilTest().adjustmentFactors().medium() + ", HIGH x" + kb.soilTest().adjustmentFactors().high()
                + " [" + kb.soilTest().adjustmentFactors().status() + "]. Not an STCR equation.");
        assumptions.add("Soil N, P, K are read as plant-available kg/ha on the elemental basis; requirements are "
                + "kg/ha of N, P2O5, K2O.");
        assumptions.add("Carry-over from previous crops, organic manures and residues is not credited ["
                + "PROTOTYPE_ASSUMPTION].");
        schedule.splits().stream().filter(s -> "MAPPING_ASSUMPTION".equals(s.status()))
                .forEach(s -> assumptions.add("Stage mapping for " + s.stage() + ": " + s.timing()
                        + " [MAPPING_ASSUMPTION]."));
        if (schedule.note() != null) {
            assumptions.add(schedule.note());
        }

        return new NutrientRequirement(kb.id(), kb.version(), crop.code(), current, profileInfo,
                crop.profiles().stream().map(Profile::code).toList(), in.areaHa(), soil, previous,
                List.copyOf(lines), dueNowKgHa, NutrientUnits.forField(dueNowKgHa, in.areaHa()),
                new NutrientAmounts(remaining[0], remaining[1], remaining[2]), List.copyOf(scheduleEntries),
                List.copyOf(warnings), List.copyOf(assumptions));
    }

    private void validate(RequirementInput in) {
        if (in == null) {
            throw new EngineInputException("Input is required.");
        }
        if (in.cropCode() == null || in.cropCode().isBlank()) {
            throw new EngineInputException("The field has no crop; set a crop before calculating a requirement.");
        }
        if (in.stageCode() == null || in.stageCode().isBlank()) {
            throw new EngineInputException(
                    "The field has no growth stage; set the current stage before calculating a requirement.");
        }
        if (!(in.areaHa() > 0) || !Double.isFinite(in.areaHa())) {
            throw new EngineInputException("Field area must be a positive number of hectares.");
        }
        if (in.asOf() == null) {
            throw new EngineInputException("Calculation date is required.");
        }
        if (in.cropStages().isEmpty()) {
            throw new EngineInputException("Crop " + in.cropCode() + " has no growth stages.");
        }
        SoilTestInput s = in.soilTest();
        if (s != null) {
            for (double v : new double[]{s.nitrogenKgHa(), s.phosphorusKgHa(), s.potassiumKgHa()}) {
                if (!(v >= 0) || !Double.isFinite(v)) {
                    throw new EngineInputException("Soil N, P and K must be non-negative numbers.");
                }
            }
            if (!(s.ph() >= 0 && s.ph() <= 14)) {
                throw new EngineInputException("Soil pH must be between 0 and 14.");
            }
            if (s.organicCarbonPct() != null && !(s.organicCarbonPct() >= 0 && s.organicCarbonPct() <= 100)) {
                throw new EngineInputException("Organic carbon must be between 0 and 100 %.");
            }
            if (s.sampleDate() == null || s.sampleDate().isAfter(in.asOf())) {
                throw new EngineInputException("Soil sample date must not be in the future.");
            }
        }
        for (AppliedFertilizer a : in.applications()) {
            NutrientAmounts t = a.fieldTotalKg();
            if (a.appliedOn() == null || t == null || t.n() < 0 || t.p2o5() < 0 || t.k2o() < 0) {
                throw new EngineInputException("Previous applications need a date and non-negative nutrients.");
            }
        }
    }

    private Profile selectProfile(CropKnowledge crop, RequirementInput in, String[] reason) {
        if (in.profileCode() != null && !in.profileCode().isBlank()) {
            reason[0] = "requested explicitly";
            return crop.profile(in.profileCode()).orElseThrow(() -> new EngineInputException(
                    "Unknown profile " + in.profileCode() + " for crop " + crop.code() + ". Available: "
                            + crop.profiles().stream().map(Profile::code).toList() + "."));
        }
        if (Boolean.TRUE.equals(in.rainfed()) && crop.rainfedProfile() != null) {
            reason[0] = "field is rainfed";
            return crop.profile(crop.rainfedProfile()).orElseThrow();
        }
        if (crop.lateSownProfile() != null && in.sowingDate() != null && !Boolean.TRUE.equals(in.rainfed())
                && isLateSown(crop, in.sowingDate())) {
            var rule = crop.lateSowingAfter();
            reason[0] = String.format(Locale.ROOT, "sown on %s, after %02d-%02d (late sowing)", in.sowingDate(),
                    rule.day(), rule.month());
            return crop.profile(crop.lateSownProfile()).orElseThrow();
        }
        reason[0] = Boolean.TRUE.equals(in.rainfed()) ? "crop default (no rainfed profile)" : "crop default";
        return crop.profile(crop.defaultProfile()).orElseThrow();
    }

    /**
     * Rabi crops are sown from autumn into the next calendar year: a date in January-June counts as after the
     * threshold of the season that started the previous autumn.
     */
    private static boolean isLateSown(CropKnowledge crop, LocalDate sown) {
        var rule = crop.lateSowingAfter();
        MonthDay threshold = MonthDay.of(rule.month(), rule.day());
        if (sown.getMonthValue() <= 6) {
            return true;
        }
        return MonthDay.from(sown).isAfter(threshold);
    }

    private SoilAssessment assessSoil(RequirementInput in, Map<Nutrient, SoilFertilityClass> classes,
                                      List<String> warnings) {
        SoilTestInput s = in.soilTest();
        var soilKb = kb.soilTest();
        if (s == null) {
            for (Nutrient n : Nutrient.values()) {
                classes.put(n, SoilFertilityClass.ASSUMED_MEDIUM);
            }
            warnings.add("No soil test on record: the general recommendation is used without soil-test "
                    + "adjustment. Add a soil test for a field-specific requirement.");
            return new SoilAssessment(false, null, null, null, null, null, null, null, null, null, null, null);
        }
        classes.put(Nutrient.N, classify(s.nitrogenKgHa(), soilKb.ratings().get("N")));
        classes.put(Nutrient.P2O5, classify(s.phosphorusKgHa(), soilKb.ratings().get("P")));
        classes.put(Nutrient.K2O, classify(s.potassiumKgHa(), soilKb.ratings().get("K")));

        long ageDays = ChronoUnit.DAYS.between(s.sampleDate(), in.asOf());
        if (ageDays > soilKb.maxAgeDays().value()) {
            warnings.add(String.format(Locale.ROOT, "Soil test is %d days old (sampled %s); results older than "
                    + "%.0f days may no longer represent the field. Consider re-testing.", ageDays, s.sampleDate(),
                    soilKb.maxAgeDays().value()));
        }
        if (s.ph() < soilKb.ph().stronglyAcidBelow()) {
            warnings.add(String.format(Locale.ROOT, "Soil pH %.2f is strongly acidic (< %.1f). Nutrient availability "
                    + "(especially P) may be limited and liming may be needed; this is not included in the "
                    + "calculation. Consult a soil-testing laboratory.", s.ph(), soilKb.ph().stronglyAcidBelow()));
        } else if (s.ph() > soilKb.ph().stronglyAlkalineAbove()) {
            warnings.add(String.format(Locale.ROOT, "Soil pH %.2f is strongly alkaline (> %.1f). Nutrient availability "
                    + "may be limited and sodicity should be checked; this is not included in the calculation. "
                    + "Consult a soil-testing laboratory.", s.ph(), soilKb.ph().stronglyAlkalineAbove()));
        }
        SoilFertilityClass oc = s.organicCarbonPct() == null ? null
                : classify(s.organicCarbonPct(), soilKb.organicCarbonPercent());
        return new SoilAssessment(true, s.recordId(), s.sampleDate(), ageDays, s.nitrogenKgHa(), s.phosphorusKgHa(),
                s.potassiumKgHa(), NutrientUnits.phosphorusToP2o5(s.phosphorusKgHa()),
                NutrientUnits.potassiumToK2o(s.potassiumKgHa()), s.ph(), s.organicCarbonPct(), oc);
    }

    /** Boundaries belong to MEDIUM: value < lowBelow is LOW, value > highAbove is HIGH. */
    static SoilFertilityClass classify(double value, Rating rating) {
        if (value < rating.lowBelow()) {
            return SoilFertilityClass.LOW;
        }
        if (value > rating.highAbove()) {
            return SoilFertilityClass.HIGH;
        }
        return SoilFertilityClass.MEDIUM;
    }

    private double factor(SoilFertilityClass cls) {
        var f = kb.soilTest().adjustmentFactors();
        return switch (cls) {
            case LOW -> f.low();
            case HIGH -> f.high();
            case MEDIUM, ASSUMED_MEDIUM -> f.medium();
        };
    }

    private PreviousUsage previousUsage(RequirementInput in, List<String> warnings) {
        var cfg = kb.previousApplications();
        LocalDate start;
        if (in.sowingDate() != null) {
            start = in.sowingDate().minusDays((long) cfg.windowDaysBeforeSowing().value());
        } else {
            start = in.asOf().minusDays((long) cfg.fallbackLookbackDays().value());
            if (!in.applications().isEmpty()) {
                warnings.add(String.format(Locale.ROOT, "The field has no sowing date, so fertilizer applied in the "
                        + "last %.0f days is counted as this crop's.", cfg.fallbackLookbackDays().value()));
            }
        }
        LocalDate end = in.asOf();
        NutrientAmounts total = NutrientAmounts.ZERO;
        int counted = 0;
        int outside = 0;
        for (AppliedFertilizer a : in.applications()) {
            if (!a.appliedOn().isBefore(start) && !a.appliedOn().isAfter(end)) {
                total = total.plus(a.fieldTotalKg());
                counted++;
            } else {
                outside++;
            }
        }
        return new PreviousUsage(start, end, counted, outside, total, NutrientUnits.perHectare(total, in.areaHa()));
    }
}
