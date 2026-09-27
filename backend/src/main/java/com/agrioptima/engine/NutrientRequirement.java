package com.agrioptima.engine;

import java.time.LocalDate;
import java.util.List;

/**
 * Deterministic output of {@link NutrientRequirementEngine}. {@link #dueNowKgHa()} is the requirement handed to
 * the optimizer (Milestone 5): the nutrients to supply at the current growth stage, after soil-test adjustment
 * and after subtracting what this crop has already received.
 */
public record NutrientRequirement(
        String knowledgeBaseId,
        String knowledgeBaseVersion,
        String cropCode,
        RequirementInput.StageRef stage,
        ProfileInfo profile,
        List<String> availableProfiles,
        double areaHa,
        SoilAssessment soil,
        PreviousUsage previousUsage,
        List<NutrientLine> nutrients,
        NutrientAmounts dueNowKgHa,
        NutrientAmounts dueNowFieldKg,
        NutrientAmounts remainingSeasonKgHa,
        List<ScheduleEntry> schedule,
        List<String> warnings,
        List<String> assumptions) {

    public record ProfileInfo(String code, String description, String status, String sourceId, String sourceCitation,
                              String selectedBecause) {
    }

    public record SoilAssessment(boolean soilTestUsed, Long recordId, LocalDate sampleDate, Long ageDays,
                                 Double availableNKgHa, Double availablePKgHa, Double availableKKgHa,
                                 Double availableP2o5EquivalentKgHa, Double availableK2oEquivalentKgHa,
                                 Double ph, Double organicCarbonPct, SoilFertilityClass organicCarbonClass) {
    }

    public record PreviousUsage(LocalDate windowStart, LocalDate windowEnd, int applicationsCounted,
                                int applicationsOutsideWindow, NutrientAmounts appliedFieldKg,
                                NutrientAmounts appliedKgHa) {
    }

    /**
     * One nutrient's calculation, all kg/ha except the field totals.
     * adjusted = general x factor; remaining = max(0, adjusted - applied);
     * dueNow = max(0, adjusted x cumulativeShareDue - applied); excess = max(0, applied - adjusted).
     */
    public record NutrientLine(Nutrient nutrient, double generalRecommendationKgHa, SoilFertilityClass soilClass,
                               double soilAdjustmentFactor, double adjustedSeasonTargetKgHa,
                               double cumulativeShareDue, double alreadyAppliedKgHa, double remainingSeasonKgHa,
                               double dueNowKgHa, double excessAppliedKgHa, double dueNowFieldKg,
                               double remainingSeasonFieldKg) {
    }

    public enum StagePosition { PAST, CURRENT, UPCOMING }

    /** Planned dose for one scheduled application of the adjusted season target. */
    public record ScheduleEntry(String stageCode, String stageName, int seq, StagePosition position,
                                NutrientAmounts share, NutrientAmounts plannedKgHa, String timing, String status) {
    }
}
