package com.agrioptima.dto.field;

import com.agrioptima.entity.IrrigationType;
import com.agrioptima.entity.Season;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FieldRequest(
        @NotBlank @Size(max = 100)
        String name,

        @NotNull @DecimalMin(value = "0.001") @DecimalMax("100000") @Digits(integer = 7, fraction = 3)
        BigDecimal areaHa,

        @Size(max = 40)
        String soilType,

        IrrigationType irrigationType,

        @Positive
        Long cropId,

        @Positive
        Long growthStageId,

        Season season,

        LocalDate sowingDate,

        @Size(max = 100)
        String previousCrop
) {

    @JsonIgnore
    @AssertTrue(message = "growthStageId requires cropId")
    public boolean isStageWithCrop() {
        return growthStageId == null || cropId != null;
    }
}
