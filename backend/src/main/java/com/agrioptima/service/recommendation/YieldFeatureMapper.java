package com.agrioptima.service.recommendation;

import com.agrioptima.entity.IrrigationType;
import com.agrioptima.ml.MlContracts.Scenario;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps what the backend stores about a field to the yield model's feature contract (Milestone 3/6
 * {@code YieldScenario}). Every input is reported with where it came from; nothing farmer-specific is invented:
 * <ul>
 *   <li>{@code FIELD} - stored on the field as is</li>
 *   <li>{@code MAPPED} - derived from a stored value by a documented mapping (state from the farm location,
 *       soil texture from the soil type)</li>
 *   <li>{@code DERIVED} - computed (season nutrient totals, zinc from the plan's products)</li>
 *   <li>{@code PROTOTYPE_DEFAULT} - not stored; a labelled default is used</li>
 *   <li>{@code NOT_RECORDED} - not stored; sent empty, the model's pipeline imputes the training value</li>
 * </ul>
 * If a required input (state, sowing date) cannot be supplied, no prediction is requested.
 */
public final class YieldFeatureMapper {

    public enum Source { FIELD, MAPPED, DERIVED, PROTOTYPE_DEFAULT, NOT_RECORDED }

    public record Input(String feature, String value, Source source, String note) {
    }

    /** Field-level part of the scenario; nutrients are added per plan. */
    public record Base(boolean available, String unavailableReason, String crop, String state, LocalDate sowingDate,
                       String soilTexture, String previousCrop, boolean irrigationAvailable, boolean fymApplied,
                       List<Input> inputs) {

        public Scenario scenario(double nKgHa, double p2o5KgHa, double k2oKgHa) {
            return new Scenario(crop, state, sowingDate, soilTexture, null, previousCrop, irrigationAvailable,
                    fymApplied, false, nKgHa, p2o5KgHa, k2oKgHa);
        }
    }

    /** States the model was trained on (artifact metadata {@code categories.state}). */
    public static final List<String> MODEL_STATES = List.of("ANDHRA_PRADESH", "BIHAR", "CHHATTISGARH", "HARYANA",
            "ODISHA", "PUNJAB", "UTTAR_PRADESH", "WEST_BENGAL");

    private YieldFeatureMapper() {
    }

    public static Base map(String cropCode, String farmLocation, LocalDate sowingDate, String soilType,
                           IrrigationType irrigation, String previousCrop) {
        List<Input> inputs = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        inputs.add(new Input("crop", cropCode, Source.FIELD, null));

        List<String> states = statesIn(farmLocation);
        String state = states.size() == 1 ? states.get(0) : null;
        if (state != null) {
            inputs.add(new Input("state", state, Source.MAPPED, "found in the farm location '" + farmLocation + "'"));
        } else {
            String why = states.isEmpty()
                    ? "no supported state name found in the farm location (" + (farmLocation == null ? "not set"
                    : "'" + farmLocation + "'") + "); add one of " + String.join(", ", MODEL_STATES)
                    + " to the location, e.g. 'Patna, Bihar'"
                    : "the farm location names more than one state: " + String.join(", ", states);
            inputs.add(new Input("state", null, Source.NOT_RECORDED, why));
            missing.add("state: " + why);
        }

        if (sowingDate != null) {
            inputs.add(new Input("sowing_date", sowingDate.toString(), Source.FIELD, null));
        } else {
            inputs.add(new Input("sowing_date", null, Source.NOT_RECORDED, "set the field's sowing date"));
            missing.add("sowing date: not set on the field");
        }

        String texture = soilTexture(soilType);
        inputs.add(texture != null
                ? new Input("soil_texture", texture, Source.MAPPED, "from soil type '" + soilType + "'")
                : new Input("soil_texture", null, Source.NOT_RECORDED, soilType == null
                ? "field has no soil type; the model imputes the most frequent training value"
                : "soil type '" + soilType + "' has no texture mapping; the model imputes the most frequent value"));

        boolean irrigated = irrigation != IrrigationType.RAINFED;
        inputs.add(irrigation != null
                ? new Input("irrigation_available", String.valueOf(irrigated), Source.FIELD, "irrigation type " + irrigation)
                : new Input("irrigation_available", "true", Source.PROTOTYPE_DEFAULT,
                "irrigation type not set; assumed irrigated, as the requirement engine's default (non-rainfed) profile"));

        inputs.add(new Input("variety_type", null, Source.NOT_RECORDED,
                "not stored; the model imputes the most frequent training value"));
        String prev = previousCrop == null || previousCrop.isBlank() ? null : previousCrop.trim();
        inputs.add(prev != null
                ? new Input("previous_crop", prev, Source.FIELD, "grouped by the ML service (rice/wheat/fallow/pulse/maize/other)")
                : new Input("previous_crop", null, Source.NOT_RECORDED,
                "not set; the model imputes the most frequent training value"));
        inputs.add(new Input("fym_applied", "false", Source.PROTOTYPE_DEFAULT, "FYM use is not recorded; assumed none"));
        inputs.add(new Input("zn_applied", "false", Source.DERIVED,
                "no candidate plan contains a zinc fertilizer (the catalogue has none)"));
        inputs.add(new Input("n_kg_ha, p2o5_kg_ha, k2o_kg_ha", null, Source.DERIVED,
                "season totals per plan: already applied + this plan + later splits of the schedule"));

        boolean available = missing.isEmpty();
        return new Base(available, available ? null : "Missing model inputs - " + String.join("; ", missing),
                cropCode, state, sowingDate, texture, prev, irrigated, false, List.copyOf(inputs));
    }

    /** Whole-word state names (spaces, hyphens or underscores between words) in a free-text location. */
    static List<String> statesIn(String location) {
        if (location == null || location.isBlank()) {
            return List.of();
        }
        String text = " " + location.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", " ").trim() + " ";
        text = text.replace(" CHATTISGARH ", " CHHATTISGARH ");
        List<String> found = new ArrayList<>();
        for (String state : MODEL_STATES) {
            if (text.contains(" " + state.replace('_', ' ') + " ")) {
                found.add(state);
            }
        }
        return found;
    }

    /**
     * Mapping assumption: clay / black (vertisol) -> HEAVY; sand -> LIGHT; loam / silt / alluvial -> MEDIUM,
     * checked in that order ("sandy loam" -> LIGHT, "clay loam" -> HEAVY). LIGHT/MEDIUM/HEAVY are accepted as is.
     */
    static String soilTexture(String soilType) {
        if (soilType == null || soilType.isBlank()) {
            return null;
        }
        String s = soilType.toUpperCase(Locale.ROOT);
        if (s.contains("HEAVY") || s.contains("CLAY") || s.contains("BLACK") || s.contains("VERTISOL")) {
            return "HEAVY";
        }
        if (s.contains("LIGHT") || s.contains("SAND")) {
            return "LIGHT";
        }
        if (s.contains("MEDIUM") || s.contains("LOAM") || s.contains("SILT") || s.contains("ALLUVIAL")) {
            return "MEDIUM";
        }
        return null;
    }
}
