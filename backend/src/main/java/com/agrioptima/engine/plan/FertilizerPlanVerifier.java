package com.agrioptima.engine.plan;

import com.agrioptima.engine.NutrientAmounts;
import com.agrioptima.engine.NutrientUnits;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Independent re-check of a fertilizer plan returned by the Python optimizer (Milestone 5). Pure Java, no I/O.
 *
 * <p>Everything is recomputed from the plan's product quantities and the backend's own catalogue (grades, prices,
 * caps), never from numbers the optimizer reports. A plan is valid only if every quantity is finite, non-negative
 * and within its cap, every required nutrient is supplied (within {@link #REQUIREMENT_TOLERANCE_KG_HA}), and every
 * total the optimizer claims matches the recomputed value. The optimizer itself checks supply with no tolerance;
 * the tolerance here only absorbs summation-order differences between Python and Java (~1e-13).
 */
public final class FertilizerPlanVerifier {

    /** 1 microgram/ha. Agronomically nil; far larger than double round-off on these magnitudes. */
    public static final double REQUIREMENT_TOLERANCE_KG_HA = 1e-9;
    /** Relative tolerance when comparing a claimed total with the recomputed one. */
    public static final double CONSISTENCY_TOLERANCE_REL = 1e-9;

    private FertilizerPlanVerifier() {
    }

    /** A product as the backend knows it. {@code maxKgHa} is the cap sent to the optimizer; null = no cap. */
    public record ProductSpec(String code, double nPct, double p2o5Pct, double k2oPct, double pricePerKg,
                              Double maxKgHa) {
    }

    /** One product in a plan: kg/ha and the whole-field kg the optimizer reported. */
    public record PlanLine(String code, double kgHa, double fieldKg) {
    }

    /** Totals the optimizer reported for the plan; all are re-checked. */
    public record ClaimedTotals(NutrientAmounts suppliedKgHa, NutrientAmounts suppliedFieldKg,
                                NutrientAmounts excessKgHa, NutrientAmounts excessFieldKg,
                                double costPerHa, double fieldCost, double totalMassKgHa, double totalMassFieldKg,
                                boolean feasible) {
    }

    /** {@code claimed} may be null when only the quantities are to be checked (e.g. a user-edited plan). */
    public record CandidatePlan(String strategy, List<PlanLine> lines, ClaimedTotals claimed) {
    }

    /** Result of the re-check; the metrics are recomputed, not copied from the optimizer. */
    public record PlanVerification(String strategy, boolean valid, List<String> violations,
                                   NutrientAmounts suppliedKgHa, NutrientAmounts suppliedFieldKg,
                                   NutrientAmounts excessKgHa, NutrientAmounts excessFieldKg,
                                   double costPerHa, double fieldCost, double totalMassKgHa,
                                   double totalMassFieldKg) {
    }

    public static PlanVerification verify(NutrientAmounts requiredKgHa, double areaHa, List<ProductSpec> catalogue,
                                          CandidatePlan plan) {
        NutrientUnits.forField(NutrientAmounts.ZERO, areaHa); // validates area (> 0, finite)
        Map<String, ProductSpec> products = catalogue.stream()
                .collect(Collectors.toMap(ProductSpec::code, Function.identity()));
        List<String> violations = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        NutrientAmounts supplied = NutrientAmounts.ZERO;
        double cost = 0;
        double mass = 0;
        for (PlanLine line : plan.lines()) {
            ProductSpec product = products.get(line.code());
            if (product == null) {
                violations.add(line.code() + ": not an available fertilizer");
                continue;
            }
            if (!seen.add(line.code())) {
                violations.add(line.code() + ": listed more than once");
            }
            double kg = line.kgHa();
            if (!Double.isFinite(kg) || kg < 0) {
                violations.add(line.code() + ": quantity must be a finite, non-negative number (was " + kg + ")");
                continue;
            }
            if (product.maxKgHa() != null && kg > product.maxKgHa()) {
                violations.add(line.code() + ": " + kg + " kg/ha exceeds the upper bound " + product.maxKgHa());
            }
            if (!matches(line.fieldKg(), kg * areaHa)) {
                violations.add(line.code() + ": field quantity " + line.fieldKg() + " kg != " + kg + " kg/ha x "
                        + areaHa + " ha");
            }
            supplied = supplied.plus(NutrientUnits.nutrientsInProduct(kg, product.nPct(), product.p2o5Pct(),
                    product.k2oPct()));
            cost += kg * product.pricePerKg();
            mass += kg;
        }

        checkRequirement("N", supplied.n(), requiredKgHa.n(), violations);
        checkRequirement("P2O5", supplied.p2o5(), requiredKgHa.p2o5(), violations);
        checkRequirement("K2O", supplied.k2o(), requiredKgHa.k2o(), violations);

        NutrientAmounts excess = supplied.minus(requiredKgHa).clampToZero();
        NutrientAmounts suppliedField = NutrientUnits.forField(supplied, areaHa);
        NutrientAmounts excessField = NutrientUnits.forField(excess, areaHa);
        if (plan.claimed() != null) {
            ClaimedTotals c = plan.claimed();
            compare("supplied kg/ha", c.suppliedKgHa(), supplied, violations);
            compare("supplied field kg", c.suppliedFieldKg(), suppliedField, violations);
            compare("excess kg/ha", c.excessKgHa(), excess, violations);
            compare("excess field kg", c.excessFieldKg(), excessField, violations);
            compare("cost per ha", c.costPerHa(), cost, violations);
            compare("field cost", c.fieldCost(), cost * areaHa, violations);
            compare("total mass kg/ha", c.totalMassKgHa(), mass, violations);
            compare("total mass field kg", c.totalMassFieldKg(), mass * areaHa, violations);
        }
        boolean valid = violations.isEmpty();
        if (plan.claimed() != null && plan.claimed().feasible() != valid) {
            violations.add("optimizer reported feasible=" + plan.claimed().feasible() + " but the re-check says "
                    + valid);
            valid = false;
        }
        return new PlanVerification(plan.strategy(), valid, List.copyOf(violations), supplied, suppliedField,
                excess, excessField, cost, cost * areaHa, mass, mass * areaHa);
    }

    private static void checkRequirement(String nutrient, double supplied, double required, List<String> violations) {
        if (supplied < required - REQUIREMENT_TOLERANCE_KG_HA) {
            violations.add(nutrient + ": supplies " + supplied + " kg/ha, requirement is " + required + " kg/ha");
        }
    }

    private static void compare(String what, NutrientAmounts claimed, NutrientAmounts actual, List<String> out) {
        compare(what + " N", claimed.n(), actual.n(), out);
        compare(what + " P2O5", claimed.p2o5(), actual.p2o5(), out);
        compare(what + " K2O", claimed.k2o(), actual.k2o(), out);
    }

    private static void compare(String what, double claimed, double actual, List<String> out) {
        if (!matches(claimed, actual)) {
            out.add(what + ": optimizer reported " + claimed + ", recomputed " + actual);
        }
    }

    private static boolean matches(double claimed, double actual) {
        return Double.isFinite(claimed)
                && Math.abs(claimed - actual) <= CONSISTENCY_TOLERANCE_REL * Math.max(1, Math.abs(actual));
    }
}
