package com.agrioptima.dto.requirement;

import com.agrioptima.engine.NutrientAmounts;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** N, P2O5, K2O in kg (or kg/ha), rounded to 2 decimals for display. The engine computes unrounded values. */
public record Amounts(BigDecimal n, BigDecimal p2o5, BigDecimal k2o) {

    public static Amounts of(NutrientAmounts a) {
        return new Amounts(round(a.n()), round(a.p2o5()), round(a.k2o()));
    }

    public static BigDecimal round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal round(Double v) {
        return v == null ? null : round(v.doubleValue());
    }
}
