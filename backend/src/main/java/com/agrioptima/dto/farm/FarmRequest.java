package com.agrioptima.dto.farm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record FarmRequest(
        @NotBlank @Size(max = 100)
        String name,

        @Size(max = 150)
        String locationName,

        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6)
        BigDecimal latitude,

        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6)
        BigDecimal longitude
) {

    /** Coordinates are used for weather lookup later, so a half-specified location is rejected. */
    @JsonIgnore
    @AssertTrue(message = "latitude and longitude must be provided together")
    public boolean isCoordinatePairComplete() {
        return (latitude == null) == (longitude == null);
    }
}
