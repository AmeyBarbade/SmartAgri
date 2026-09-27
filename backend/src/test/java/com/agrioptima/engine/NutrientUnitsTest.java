package com.agrioptima.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class NutrientUnitsTest {

    @Test
    void oxideConversionFactorsMatchAtomicWeights() {
        assertThat(NutrientUnits.P_TO_P2O5).isCloseTo(2.2914, within(1e-4));
        assertThat(NutrientUnits.K_TO_K2O).isCloseTo(1.2046, within(1e-4));
    }

    @Test
    void elementalAndOxideConversionsRoundTrip() {
        assertThat(NutrientUnits.phosphorusToP2o5(10)).isCloseTo(22.914, within(1e-3));
        assertThat(NutrientUnits.potassiumToK2o(100)).isCloseTo(120.46, within(1e-2));
        assertThat(NutrientUnits.p2o5ToPhosphorus(NutrientUnits.phosphorusToP2o5(17.3))).isCloseTo(17.3, within(1e-9));
        assertThat(NutrientUnits.k2oToPotassium(NutrientUnits.potassiumToK2o(250))).isCloseTo(250, within(1e-9));
    }

    @Test
    void productQuantityToNutrients() {
        NutrientAmounts urea = NutrientUnits.nutrientsInProduct(100, 46, 0, 0);
        assertThat(urea.n()).isCloseTo(46, within(1e-9));
        NutrientAmounts dap = NutrientUnits.nutrientsInProduct(50, 18, 46, 0);
        assertThat(dap.n()).isCloseTo(9, within(1e-9));
        assertThat(dap.p2o5()).isCloseTo(23, within(1e-9));
        NutrientAmounts npk = NutrientUnits.nutrientsInProduct(200, 10, 26, 26);
        assertThat(npk).isEqualTo(new NutrientAmounts(20, 52, 52));
    }

    @Test
    void perHectareAndFieldTotalsAreInverse() {
        NutrientAmounts field = new NutrientAmounts(100, 50, 25);
        NutrientAmounts perHa = NutrientUnits.perHectare(field, 2.5);
        assertThat(perHa).isEqualTo(new NutrientAmounts(40, 20, 10));
        assertThat(NutrientUnits.forField(perHa, 2.5)).isEqualTo(field);
        assertThat(NutrientUnits.forField(new NutrientAmounts(120, 60, 40), 0.25))
                .isEqualTo(new NutrientAmounts(30, 15, 10));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void nonPositiveOrNonFiniteAreaIsRejected(double area) {
        assertThatThrownBy(() -> NutrientUnits.perHectare(NutrientAmounts.ZERO, area))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NutrientUnits.forField(NutrientAmounts.ZERO, area))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidProductInputsAreRejected() {
        assertThatThrownBy(() -> NutrientUnits.nutrientsInProduct(-1, 46, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NutrientUnits.nutrientsInProduct(10, 101, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NutrientAmounts(Double.NaN, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
