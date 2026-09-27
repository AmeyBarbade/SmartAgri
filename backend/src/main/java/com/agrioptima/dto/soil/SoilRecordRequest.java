package com.agrioptima.dto.soil;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A soil test result. N/P/K are plant-available amounts in kg/ha.
 * Micronutrients (S, Zn, Fe, Cu, Mn, B) are in ppm (mg/kg).
 * EC is in dS/m.
 */
public record SoilRecordRequest(
        @NotNull @PastOrPresent
        LocalDate sampleDate,

        @NotNull @DecimalMin("0") @DecimalMax("2000") @Digits(integer = 6, fraction = 2)
        BigDecimal nitrogen,

        @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 6, fraction = 2)
        BigDecimal phosphorus,

        @NotNull @DecimalMin("0") @DecimalMax("3000") @Digits(integer = 6, fraction = 2)
        BigDecimal potassium,

        @NotNull @DecimalMin("3.0") @DecimalMax("11.0") @Digits(integer = 2, fraction = 2)
        BigDecimal ph,

        /* Percent. */
        @DecimalMin("0") @DecimalMax("20") @Digits(integer = 3, fraction = 2)
        BigDecimal organicCarbon,

        /* Percent. */
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal moisture,

        /* Sulfur in ppm. */
        @DecimalMin("0") @DecimalMax("500") @Digits(integer = 5, fraction = 2)
        BigDecimal sulfur,

        /* Zinc in ppm. */
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 4, fraction = 2)
        BigDecimal zinc,

        /* Iron in ppm. */
        @DecimalMin("0") @DecimalMax("200") @Digits(integer = 4, fraction = 2)
        BigDecimal iron,

        /* Copper in ppm. */
        @DecimalMin("0") @DecimalMax("50") @Digits(integer = 3, fraction = 2)
        BigDecimal copper,

        /* Manganese in ppm. */
        @DecimalMin("0") @DecimalMax("200") @Digits(integer = 4, fraction = 2)
        BigDecimal manganese,

        /* Boron in ppm. */
        @DecimalMin("0") @DecimalMax("50") @Digits(integer = 3, fraction = 2)
        BigDecimal boron,

        /* Electrical Conductivity in dS/m. */
        @DecimalMin("0") @DecimalMax("50") @Digits(integer = 3, fraction = 2)
        BigDecimal ec,

        @Size(max = 500)
        String notes
) {
}
