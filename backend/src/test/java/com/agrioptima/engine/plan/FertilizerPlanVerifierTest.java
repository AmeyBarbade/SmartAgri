package com.agrioptima.engine.plan;

import com.agrioptima.engine.NutrientAmounts;
import com.agrioptima.engine.plan.FertilizerPlanVerifier.CandidatePlan;
import com.agrioptima.engine.plan.FertilizerPlanVerifier.ClaimedTotals;
import com.agrioptima.engine.plan.FertilizerPlanVerifier.PlanLine;
import com.agrioptima.engine.plan.FertilizerPlanVerifier.PlanVerification;
import com.agrioptima.engine.plan.FertilizerPlanVerifier.ProductSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FertilizerPlanVerifierTest {

    /** V2 reference data grades and prices; the optimizer's default cap of 500 kg/ha. */
    private static final List<ProductSpec> CATALOGUE = List.of(
            new ProductSpec("UREA", 46, 0, 0, 5.92, 500.0),
            new ProductSpec("DAP", 18, 46, 0, 27.00, 500.0),
            new ProductSpec("MOP", 0, 0, 60, 34.00, 500.0),
            new ProductSpec("NPK_10_26_26", 10, 26, 26, 29.40, 500.0),
            new ProductSpec("SSP", 0, 16, 0, 11.00, 500.0));

    private static PlanVerification check(NutrientAmounts required, double area, PlanLine... lines) {
        return FertilizerPlanVerifier.verify(required, area, CATALOGUE, new CandidatePlan("TEST", List.of(lines), null));
    }

    private static PlanLine line(String code, double kgHa, double area) {
        return new PlanLine(code, kgHa, kgHa * area);
    }

    @Test
    void nOnlyPlanIsRecomputedFromQuantities() {
        PlanVerification v = check(new NutrientAmounts(40, 0, 0), 2, line("UREA", 86.957, 2));

        assertThat(v.valid()).isTrue();
        assertThat(v.violations()).isEmpty();
        assertThat(v.suppliedKgHa().n()).isCloseTo(40.00022, within(1e-9));
        assertThat(v.excessKgHa().n()).isCloseTo(0.00022, within(1e-9));
        assertThat(v.costPerHa()).isCloseTo(86.957 * 5.92, within(1e-9));
        assertThat(v.fieldCost()).isCloseTo(86.957 * 5.92 * 2, within(1e-9));
        assertThat(v.totalMassFieldKg()).isCloseTo(173.914, within(1e-9));
        assertThat(v.suppliedFieldKg().n()).isCloseTo(80.00044, within(1e-9));
    }

    @Test
    void oneGramShortOfTheRequirementIsRejected() {
        PlanVerification v = check(new NutrientAmounts(40, 0, 0), 1, line("UREA", 86.956, 1)); // 39.99976 kg N

        assertThat(v.valid()).isFalse();
        assertThat(v.violations()).singleElement().asString().startsWith("N: supplies 39.99976");
    }

    @Test
    void nitrogenFromDapCountsTowardsTheRequirement() {
        // M4 example 6 LOWEST_COST plan: DAP + NPK supply P and K and 23.2 kg N nobody asked for
        PlanVerification v = check(new NutrientAmounts(0, 60, 40), 1,
                line("DAP", 43.479, 1), line("NPK_10_26_26", 153.847, 1));

        assertThat(v.valid()).isTrue();
        assertThat(v.suppliedKgHa().n()).isCloseTo(43.479 * 0.18 + 153.847 * 0.10, within(1e-9));
        assertThat(v.excessKgHa().n()).isCloseTo(23.21092, within(1e-9));
        assertThat(v.excessKgHa().p2o5()).isCloseTo(43.479 * 0.46 + 153.847 * 0.26 - 60, within(1e-9));
    }

    @Test
    void emptyPlanIsValidOnlyWhenNothingIsRequired() {
        assertThat(check(NutrientAmounts.ZERO, 1).valid()).isTrue();
        assertThat(check(new NutrientAmounts(0, 0, 0.001), 1).valid()).isFalse();
    }

    @Test
    void toleranceOnlyAbsorbsRoundOff() {
        double urea = 1 / 0.46;
        NutrientAmounts suppliedExactly = new NutrientAmounts(urea * 46 / 100, 0, 0);
        assertThat(check(suppliedExactly.plus(new NutrientAmounts(5e-10, 0, 0)), 1, line("UREA", urea, 1)).valid())
                .isTrue();
        assertThat(check(suppliedExactly.plus(new NutrientAmounts(2e-9, 0, 0)), 1, line("UREA", urea, 1)).valid())
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1, Double.NaN, Double.POSITIVE_INFINITY})
    void impossibleQuantitiesAreRejected(double kg) {
        PlanVerification v = check(new NutrientAmounts(40, 0, 0), 1, new PlanLine("UREA", kg, kg),
                line("UREA", 100, 1));
        assertThat(v.valid()).isFalse();
        assertThat(v.violations()).anyMatch(s -> s.contains("finite, non-negative"));
    }

    @Test
    void upperBoundUnknownAndDuplicateProductsAreRejected() {
        assertThat(check(new NutrientAmounts(40, 0, 0), 1, line("UREA", 500.001, 1)).violations())
                .singleElement().asString().contains("exceeds the upper bound 500.0");
        assertThat(check(new NutrientAmounts(40, 0, 0), 1, line("CAN", 200, 1)).violations())
                .anyMatch(s -> s.startsWith("CAN: not an available fertilizer"));
        assertThat(check(new NutrientAmounts(40, 0, 0), 1, line("UREA", 50, 1), line("UREA", 50, 1)).violations())
                .anyMatch(s -> s.contains("listed more than once"));
    }

    @Test
    void fieldQuantityMustMatchPerHectareTimesArea() {
        PlanVerification v = check(new NutrientAmounts(40, 0, 0), 2, new PlanLine("UREA", 86.957, 86.957));
        assertThat(v.valid()).isFalse();
        assertThat(v.violations()).singleElement().asString().contains("field quantity");
    }

    @Test
    void claimedTotalsMustMatchTheRecomputation() {
        NutrientAmounts supplied = new NutrientAmounts(86.957 * 0.46, 0, 0);
        NutrientAmounts excess = supplied.minus(new NutrientAmounts(40, 0, 0));
        double cost = 86.957 * 5.92;
        ClaimedTotals honest = new ClaimedTotals(supplied, supplied, excess, excess, cost, cost, 86.957, 86.957, true);
        CandidatePlan plan = new CandidatePlan("LOWEST_COST", List.of(line("UREA", 86.957, 1)), honest);
        assertThat(FertilizerPlanVerifier.verify(new NutrientAmounts(40, 0, 0), 1, CATALOGUE, plan).valid()).isTrue();

        ClaimedTotals cheaper = new ClaimedTotals(supplied, supplied, excess, excess, cost * 0.9, cost, 86.957,
                86.957, true);
        PlanVerification v = FertilizerPlanVerifier.verify(new NutrientAmounts(40, 0, 0), 1, CATALOGUE,
                new CandidatePlan("LOWEST_COST", plan.lines(), cheaper));
        assertThat(v.valid()).isFalse();
        assertThat(v.violations()).anyMatch(s -> s.startsWith("cost per ha"));
        assertThat(v.violations()).anyMatch(s -> s.startsWith("optimizer reported feasible=true"));
    }

    @Test
    void optimizerClaimingInfeasibleForAValidPlanIsAlsoAMismatch() {
        NutrientAmounts supplied = new NutrientAmounts(46, 0, 0);
        NutrientAmounts excess = new NutrientAmounts(6, 0, 0);
        ClaimedTotals claimed = new ClaimedTotals(supplied, supplied, excess, excess, 592, 592, 100, 100, false);
        PlanVerification v = FertilizerPlanVerifier.verify(new NutrientAmounts(40, 0, 0), 1, CATALOGUE,
                new CandidatePlan("X", List.of(line("UREA", 100, 1)), claimed));
        assertThat(v.valid()).isFalse();
    }

    @Test
    void tinyAndHugeFieldsScaleExactly() {
        for (double area : new double[]{0.001, 9_999_999.999}) {
            PlanVerification v = check(new NutrientAmounts(40, 0, 0), area, line("UREA", 86.957, area));
            assertThat(v.valid()).isTrue();
            assertThat(v.totalMassFieldKg()).isCloseTo(86.957 * area, within(1e-9 * Math.max(1, 86.957 * area)));
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN})
    void invalidAreaIsRejected(double area) {
        assertThatThrownBy(() -> check(NutrientAmounts.ZERO, area)).isInstanceOf(IllegalArgumentException.class);
    }
}
