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
 * The upper bounds are sanity limits to catch data-entry errors (e.g. wrong units),
 * NOT agronomic thresholds; fertility ratings are applied in the recommendation engine.
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

        @Size(max = 500)
        String notes
) {
}
