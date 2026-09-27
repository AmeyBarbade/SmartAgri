package com.agrioptima.dto.requirement;

import com.agrioptima.engine.Nutrient;
import com.agrioptima.engine.NutrientRequirement;
import com.agrioptima.engine.NutrientRequirement.StagePosition;
import com.agrioptima.engine.SoilFertilityClass;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Deterministic nutrient requirement for a field. Units: kg/ha of N, P2O5, K2O unless a name ends in
 * {@code FieldKg} (kg for the whole field). {@code requirementForOptimizer} is what the optimizer will receive.
 */
public record NutrientRequirementResponse(
        Long fieldId,
        String fieldName,
        BigDecimal areaHa,
        LocalDate calculatedFor,
        String cropCode,
        Stage stage,
        NutrientRequirement.ProfileInfo profile,
        List<String> availableProfiles,
        Soil soil,
        Previous previousApplications,
        List<Line> nutrients,
        OptimizerRequirement requirementForOptimizer,
        Amounts remainingSeasonKgHa,
        List<ScheduleItem> schedule,
        List<String> warnings,
        List<String> assumptions,
        KnowledgeBaseRef knowledgeBase,
        String disclaimer) {

    public record Stage(String code, String name, int seq) {
    }

    public record Soil(boolean soilTestUsed, Long soilRecordId, LocalDate sampleDate, Long ageDays,
                       BigDecimal availableN, BigDecimal availableP, BigDecimal availableK,
                       BigDecimal availableP2o5Equivalent, BigDecimal availableK2oEquivalent, BigDecimal ph,
                       BigDecimal organicCarbonPct, SoilFertilityClass organicCarbonClass) {
    }

    public record Previous(LocalDate windowStart, LocalDate windowEnd, int applicationsCounted,
                           int applicationsOutsideWindow, Amounts appliedFieldKg, Amounts appliedKgHa) {
    }

    public record Line(Nutrient nutrient, BigDecimal generalRecommendation, SoilFertilityClass soilClass,
                       BigDecimal soilAdjustmentFactor, BigDecimal adjustedSeasonTarget, BigDecimal cumulativeShareDue,
                       BigDecimal alreadyApplied, BigDecimal remainingSeason, BigDecimal dueNow,
                       BigDecimal excessApplied, BigDecimal dueNowFieldKg, BigDecimal remainingSeasonFieldKg) {
    }

    /** Nutrients to supply at the current stage (kg/ha) and for the whole field (kg). */
    public record OptimizerRequirement(Amounts kgPerHa, Amounts fieldKg, BigDecimal areaHa, String stageCode) {
    }

    public record ScheduleItem(String stageCode, String stageName, int seq, StagePosition position, Amounts share,
                               Amounts plannedKgHa, String timing, String status) {
    }

    public record KnowledgeBaseRef(String id, String version, String status) {
    }

    public static NutrientRequirementResponse from(Long fieldId, String fieldName, LocalDate asOf,
                                                   NutrientRequirement r, String kbStatus, String disclaimer) {
        var s = r.soil();
        var p = r.previousUsage();
        BigDecimal area = Amounts.round(r.areaHa());
        return new NutrientRequirementResponse(
                fieldId, fieldName, area, asOf, r.cropCode(),
                new Stage(r.stage().code(), r.stage().name(), r.stage().seq()),
                r.profile(), r.availableProfiles(),
                new Soil(s.soilTestUsed(), s.recordId(), s.sampleDate(), s.ageDays(), Amounts.round(s.availableNKgHa()),
                        Amounts.round(s.availablePKgHa()), Amounts.round(s.availableKKgHa()),
                        Amounts.round(s.availableP2o5EquivalentKgHa()), Amounts.round(s.availableK2oEquivalentKgHa()),
                        Amounts.round(s.ph()), Amounts.round(s.organicCarbonPct()), s.organicCarbonClass()),
                new Previous(p.windowStart(), p.windowEnd(), p.applicationsCounted(), p.applicationsOutsideWindow(),
                        Amounts.of(p.appliedFieldKg()), Amounts.of(p.appliedKgHa())),
                r.nutrients().stream().map(l -> new Line(l.nutrient(), Amounts.round(l.generalRecommendationKgHa()),
                        l.soilClass(), Amounts.round(l.soilAdjustmentFactor()), Amounts.round(l.adjustedSeasonTargetKgHa()),
                        BigDecimal.valueOf(l.cumulativeShareDue()).setScale(4, java.math.RoundingMode.HALF_UP),
                        Amounts.round(l.alreadyAppliedKgHa()), Amounts.round(l.remainingSeasonKgHa()),
                        Amounts.round(l.dueNowKgHa()), Amounts.round(l.excessAppliedKgHa()),
                        Amounts.round(l.dueNowFieldKg()), Amounts.round(l.remainingSeasonFieldKg()))).toList(),
                new OptimizerRequirement(Amounts.of(r.dueNowKgHa()), Amounts.of(r.dueNowFieldKg()), area,
                        r.stage().code()),
                Amounts.of(r.remainingSeasonKgHa()),
                r.schedule().stream().map(e -> new ScheduleItem(e.stageCode(), e.stageName(), e.seq(), e.position(),
                        Amounts.of(e.share()), Amounts.of(e.plannedKgHa()), e.timing(), e.status())).toList(),
                r.warnings(), r.assumptions(),
                new KnowledgeBaseRef(r.knowledgeBaseId(), r.knowledgeBaseVersion(), kbStatus), disclaimer);
    }
}
