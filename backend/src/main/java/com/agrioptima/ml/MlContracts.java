package com.agrioptima.ml;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.LocalDate;
import java.util.List;

/**
 * Wire format of the ML service (snake_case JSON, see docs/ML_API.md). Only the fields the backend uses are
 * mapped; unknown fields are ignored. Units: nutrients kg/ha (N, P2O5, K2O), products kg/ha, costs INR, yield t/ha.
 */
public final class MlContracts {

    private MlContracts() {
    }

    // --- POST /optimize ------------------------------------------------------------------------------------------

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Nutrients(double n, double p2o5, double k2o) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record FertilizerOption(String code, String name, double nPct, double p2o5Pct, double k2oPct,
                                   double pricePerKg, Double maxKgHa) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OptimizeRequest(Nutrients requirementKgHa, double areaHa, List<FertilizerOption> fertilizers) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PlanItem(String code, String name, double kgHa, double fieldKg, double costPerHa, double fieldCost,
                           Nutrients suppliedKgHa, boolean atUpperBound) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Objective(String expression, String tieBreak, double value, String unit) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Plan(String strategy, Objective objective, List<PlanItem> items, Nutrients suppliedKgHa,
                       Nutrients suppliedFieldKg, Nutrients excessKgHa, Nutrients excessFieldKg,
                       double totalExcessKgHa, double costPerHa, double fieldCost, double totalMassKgHa,
                       double totalMassFieldKg, boolean feasible, List<String> sameAs) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Shortfall(String nutrient, double requiredKgHa, double maxSupplyKgHa, double shortfallKgHa,
                            String reason) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Solver(String library, String scipyVersion, String method) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record OptimizeResponse(String status, boolean feasible, String infeasibilityReason, double areaHa,
                                   Nutrients requirementKgHa, List<Plan> plans, List<Shortfall> infeasibility,
                                   List<String> warnings, Solver solver, String disclaimer) {
    }

    // --- POST /predict-yield -------------------------------------------------------------------------------------

    /** One scenario: the M3/M6 feature contract (YieldScenario). Optional fields may be null. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Scenario(String crop, String state, LocalDate sowingDate, String soilTexture, String varietyType,
                           String previousCrop, boolean irrigationAvailable, boolean fymApplied, boolean znApplied,
                           double nKgHa, double p2o5KgHa, double k2oKgHa) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PredictRequest(List<Scenario> scenarios) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Prediction(int index, String crop,
                             // explicit: the snake_case strategy would map "THa" to "tha"
                             @JsonProperty("predicted_yield_t_ha") Double predictedYieldTHa,
                             boolean extrapolation, List<String> clippedFeatures, String modelVersion) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PredictResponse(String modelVersion, String featureVersion, String unit,
                                  List<Prediction> predictions, boolean extrapolation, List<String> warnings,
                                  String disclaimer) {
    }

    // --- GET /weather --------------------------------------------------------------------------------------------

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record WeatherInfo(double temperature, double rainfall7dMm, boolean heavyRainWarning, String warningReason) {
    }

    // --- GET /soilgrids ------------------------------------------------------------------------------------------

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SoilGridsResponse(double nitrogenKgHa, double ph, double organicCarbonPct,
                                    boolean isFallback, String source) {
    }
}
