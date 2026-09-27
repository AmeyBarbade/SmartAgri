package com.agrioptima.engine;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the engine needs, already loaded from the database. The engine does no I/O.
 *
 * @param cropCode       crop code as in the knowledge base (WHEAT, RICE, MAIZE)
 * @param stageCode      the field's current growth stage
 * @param profileCode    optional explicit recommendation profile; null = choose from field conditions
 * @param areaHa         field area, ha (> 0)
 * @param rainfed        true if the field is rainfed, false if irrigated, null if unknown
 * @param sowingDate     optional sowing / transplanting date
 * @param asOf           calculation date ("today")
 * @param soilTest       latest soil test, or null if the field has none
 * @param applications   fertilizer already applied to the field (any date; the engine selects the season window)
 * @param cropStages     the crop's growth stages from the reference data, any order
 */
public record RequirementInput(String cropCode, String stageCode, String profileCode, double areaHa, Boolean rainfed,
                               LocalDate sowingDate, LocalDate asOf, SoilTestInput soilTest,
                               List<AppliedFertilizer> applications, List<StageRef> cropStages) {

    public RequirementInput {
        applications = applications == null ? List.of() : List.copyOf(applications);
        cropStages = cropStages == null ? List.of() : List.copyOf(cropStages);
    }

    /** Soil test on the elemental basis: available N, P, K in kg/ha; organic carbon in % (optional). */
    public record SoilTestInput(Long recordId, LocalDate sampleDate, double nitrogenKgHa, double phosphorusKgHa,
                                double potassiumKgHa, double ph, Double organicCarbonPct) {
    }

    /** One previous application: product and its nutrients for the WHOLE field (kg). */
    public record AppliedFertilizer(Long applicationId, String productCode, LocalDate appliedOn,
                                    NutrientAmounts fieldTotalKg) {
    }

    public record StageRef(String code, String name, int seq) {
    }
}
