package com.agrioptima.dto.reference;

import com.agrioptima.entity.Crop;

public record CropResponse(Long id, String code, String name, String description) {

    public static CropResponse from(Crop c) {
        return new CropResponse(c.getId(), c.getCode(), c.getName(), c.getDescription());
    }
}
