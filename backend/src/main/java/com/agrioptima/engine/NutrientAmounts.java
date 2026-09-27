package com.agrioptima.engine;

import java.util.function.DoubleUnaryOperator;

/** kg of N, P2O5 and K2O (per hectare or per field, depending on context). Immutable. */
public record NutrientAmounts(double n, double p2o5, double k2o) {

    public static final NutrientAmounts ZERO = new NutrientAmounts(0, 0, 0);

    public NutrientAmounts {
        requireFinite(n, "n");
        requireFinite(p2o5, "p2o5");
        requireFinite(k2o, "k2o");
    }

    public double get(Nutrient nutrient) {
        return switch (nutrient) {
            case N -> n;
            case P2O5 -> p2o5;
            case K2O -> k2o;
        };
    }

    public NutrientAmounts plus(NutrientAmounts other) {
        return new NutrientAmounts(n + other.n, p2o5 + other.p2o5, k2o + other.k2o);
    }

    public NutrientAmounts minus(NutrientAmounts other) {
        return new NutrientAmounts(n - other.n, p2o5 - other.p2o5, k2o - other.k2o);
    }

    public NutrientAmounts times(double factor) {
        return map(v -> v * factor);
    }

    public NutrientAmounts times(NutrientAmounts factors) {
        return new NutrientAmounts(n * factors.n, p2o5 * factors.p2o5, k2o * factors.k2o);
    }

    /** Negative components become zero. */
    public NutrientAmounts clampToZero() {
        return map(v -> Math.max(0, v));
    }

    public NutrientAmounts map(DoubleUnaryOperator op) {
        return new NutrientAmounts(op.applyAsDouble(n), op.applyAsDouble(p2o5), op.applyAsDouble(k2o));
    }

    private static void requireFinite(double v, String name) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException(name + " must be a finite number");
        }
    }
}
