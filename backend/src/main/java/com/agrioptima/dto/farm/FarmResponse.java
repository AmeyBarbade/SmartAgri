package com.agrioptima.dto.farm;

import com.agrioptima.entity.Farm;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FarmResponse(Long id, String name, String locationName, BigDecimal latitude, BigDecimal longitude,
                           long fieldCount, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static FarmResponse from(Farm farm, long fieldCount) {
        return new FarmResponse(farm.getId(), farm.getName(), farm.getLocationName(), farm.getLatitude(),
                farm.getLongitude(), fieldCount, farm.getCreatedAt(), farm.getUpdatedAt());
    }
}
