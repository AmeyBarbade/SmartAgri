package com.agrioptima.engine;

import com.agrioptima.entity.SoilRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates secondary and micronutrients (S, Zn, Fe, Cu, Mn, B) and Electrical Conductivity (EC)
 * against standard Indian soil agronomic thresholds (ICAR / Soil Health Card baselines).
 */
public final class MicronutrientCheck {

    public record Assessment(String nutrient, String name, Double value, Double threshold,
                              String unit, String status, Double deficit, String note) {
    }

    private MicronutrientCheck() {
    }

    public static List<Assessment> check(SoilRecord soil) {
        if (soil == null) {
            return List.of();
        }
        List<Assessment> list = new ArrayList<>();

        // Sulfur (S) - threshold 10.0 ppm
        if (soil.getSulfur() != null) {
            double v = soil.getSulfur().doubleValue();
            double thresh = 10.0;
            double def = Math.max(0.0, Math.round((thresh - v) * 100.0) / 100.0);
            String status = v < thresh ? "DEFICIENT" : "ADEQUATE";
            String note = v < thresh ? "Deficient in Sulfur; consider gypsum or sulfur-enriched fertilizers." : "Adequate";
            list.add(new Assessment("S", "Sulfur", v, thresh, "ppm", status, def, note));
        }

        // Zinc (Zn) - threshold 0.6 ppm
        if (soil.getZinc() != null) {
            double v = soil.getZinc().doubleValue();
            double thresh = 0.6;
            double def = Math.max(0.0, Math.round((thresh - v) * 100.0) / 100.0);
            String status = v < thresh ? "DEFICIENT" : "ADEQUATE";
            String note = v < thresh ? "Zinc deficiency detected; consider applying Zinc Sulfate (ZnSO4)." : "Adequate";
            list.add(new Assessment("Zn", "Zinc", v, thresh, "ppm", status, def, note));
        }

        // Iron (Fe) - threshold 4.5 ppm
        if (soil.getIron() != null) {
            double v = soil.getIron().doubleValue();
            double thresh = 4.5;
            double def = Math.max(0.0, Math.round((thresh - v) * 100.0) / 100.0);
            String status = v < thresh ? "DEFICIENT" : "ADEQUATE";
            String note = v < thresh ? "Iron deficiency detected; foliar FeSO4 spray recommended." : "Adequate";
            list.add(new Assessment("Fe", "Iron", v, thresh, "ppm", status, def, note));
        }

        // Copper (Cu) - threshold 0.2 ppm
        if (soil.getCopper() != null) {
            double v = soil.getCopper().doubleValue();
            double thresh = 0.2;
            double def = Math.max(0.0, Math.round((thresh - v) * 100.0) / 100.0);
            String status = v < thresh ? "DEFICIENT" : "ADEQUATE";
            String note = v < thresh ? "Copper deficiency detected." : "Adequate";
            list.add(new Assessment("Cu", "Copper", v, thresh, "ppm", status, def, note));
        }

        // Manganese (Mn) - threshold 2.0 ppm
        if (soil.getManganese() != null) {
            double v = soil.getManganese().doubleValue();
            double thresh = 2.0;
            double def = Math.max(0.0, Math.round((thresh - v) * 100.0) / 100.0);
            String status = v < thresh ? "DEFICIENT" : "ADEQUATE";
            String note = v < thresh ? "Manganese deficiency detected." : "Adequate";
            list.add(new Assessment("Mn", "Manganese", v, thresh, "ppm", status, def, note));
        }

        // Boron (B) - threshold 0.5 ppm
        if (soil.getBoron() != null) {
            double v = soil.getBoron().doubleValue();
            double thresh = 0.5;
            double def = Math.max(0.0, Math.round((thresh - v) * 100.0) / 100.0);
            String status = v < thresh ? "DEFICIENT" : "ADEQUATE";
            String note = v < thresh ? "Boron deficiency detected; consider applying Borax." : "Adequate";
            list.add(new Assessment("B", "Boron", v, thresh, "ppm", status, def, note));
        }

        // EC (Electrical Conductivity) - threshold 1.0 dS/m
        if (soil.getEc() != null) {
            double v = soil.getEc().doubleValue();
            double thresh = 1.0;
            String status = v > thresh ? "HIGH_SALINITY" : "NORMAL";
            String note = v > thresh ? "High salinity (>1.0 dS/m); potential salt injury risk." : "Normal salinity";
            list.add(new Assessment("EC", "Electrical Conductivity", v, thresh, "dS/m", status, 0.0, note));
        }

        return list;
    }
}
