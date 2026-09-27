package com.agrioptima.dto.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fertilizer already applied to a field (previous usage). {@code quantityKg} is kg of PRODUCT for the whole
 * field. The upper bound is a data-entry sanity limit, not an agronomic threshold.
 */
public record FertilizerApplicationRequest(
        @NotNull
        Long fertilizerId,

        @NotNull @PastOrPresent
        LocalDate appliedOn,

        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100000") @Digits(integer = 8, fraction = 2)
        BigDecimal quantityKg,

        Long growthStageId,

        @Size(max = 500)
        String notes
) {
}
