package com.agrioptima.dto.reference;

import com.agrioptima.entity.CropGrowthStage;

public record GrowthStageResponse(Long id, String code, String name, int seq, String description) {

    public static GrowthStageResponse from(CropGrowthStage s) {
        return new GrowthStageResponse(s.getId(), s.getCode(), s.getName(), s.getSeq(), s.getDescription());
    }
}
