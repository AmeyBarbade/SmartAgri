package com.agrioptima.engine;

/**
 * Unit conversions used by the engine. Conversion factors are derived from IUPAC standard atomic weights
 * (no agronomic judgement): P2O5 contains 2 P, K2O contains 2 K.
 */
public final class NutrientUnits {

    static final double ATOMIC_WEIGHT_P = 30.973762;
    static final double ATOMIC_WEIGHT_K = 39.0983;
    static final double ATOMIC_WEIGHT_O = 15.999;

    /** kg P2O5 per kg P (~2.2914). */
    public static final double P_TO_P2O5 = (2 * ATOMIC_WEIGHT_P + 5 * ATOMIC_WEIGHT_O) / (2 * ATOMIC_WEIGHT_P);
    /** kg K2O per kg K (~1.2046). */
    public static final double K_TO_K2O = (2 * ATOMIC_WEIGHT_K + ATOMIC_WEIGHT_O) / (2 * ATOMIC_WEIGHT_K);

    private NutrientUnits() {
    }

    public static double phosphorusToP2o5(double kgP) {
        return kgP * P_TO_P2O5;
    }

    public static double p2o5ToPhosphorus(double kgP2o5) {
        return kgP2o5 / P_TO_P2O5;
    }

    public static double potassiumToK2o(double kgK) {
        return kgK * K_TO_K2O;
    }

    public static double k2oToPotassium(double kgK2o) {
        return kgK2o / K_TO_K2O;
    }

    /** Nutrients (kg) supplied by {@code productKg} of a fertilizer with the given grade (% by weight). */
    public static NutrientAmounts nutrientsInProduct(double productKg, double nPct, double p2o5Pct, double k2oPct) {
        if (productKg < 0) {
            throw new IllegalArgumentException("product quantity must not be negative");
        }
        for (double pct : new double[]{nPct, p2o5Pct, k2oPct}) {
            if (pct < 0 || pct > 100) {
                throw new IllegalArgumentException("fertilizer grade must be between 0 and 100 %");
            }
        }
        return new NutrientAmounts(productKg * nPct / 100, productKg * p2o5Pct / 100, productKg * k2oPct / 100);
    }

    /** Whole-field amount (kg) to per-hectare amount (kg/ha). */
    public static NutrientAmounts perHectare(NutrientAmounts fieldTotalKg, double areaHa) {
        return fieldTotalKg.times(1 / requirePositiveArea(areaHa));
    }

    /** Per-hectare amount (kg/ha) to whole-field amount (kg). */
    public static NutrientAmounts forField(NutrientAmounts kgPerHa, double areaHa) {
        return kgPerHa.times(requirePositiveArea(areaHa));
    }

    private static double requirePositiveArea(double areaHa) {
        if (!(areaHa > 0) || !Double.isFinite(areaHa)) {
            throw new IllegalArgumentException("field area must be a positive number of hectares");
        }
        return areaHa;
    }
}
