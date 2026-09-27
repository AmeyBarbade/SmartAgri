package com.agrioptima.dto.field;

import com.agrioptima.entity.Crop;
import com.agrioptima.entity.CropGrowthStage;
import com.agrioptima.entity.Field;
import com.agrioptima.entity.IrrigationType;
import com.agrioptima.entity.Season;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FieldResponse(Long id, Long farmId, String name, BigDecimal areaHa, String soilType,
                            IrrigationType irrigationType, CropRef crop, StageRef growthStage, Season season,
                            LocalDate sowingDate, String previousCrop,
                            String boundaryGeojson, BigDecimal centroidLat, BigDecimal centroidLon,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {

    public record CropRef(Long id, String code, String name) {
        static CropRef of(Crop c) {
            return c == null ? null : new CropRef(c.getId(), c.getCode(), c.getName());
        }
    }

    public record StageRef(Long id, String code, String name, int seq) {
        static StageRef of(CropGrowthStage s) {
            return s == null ? null : new StageRef(s.getId(), s.getCode(), s.getName(), s.getSeq());
        }
    }

    public static FieldResponse from(Field f) {
        return new FieldResponse(f.getId(), f.getFarm().getId(), f.getName(), f.getAreaHa(), f.getSoilType(),
                f.getIrrigationType(), CropRef.of(f.getCrop()), StageRef.of(f.getGrowthStage()), f.getSeason(),
                f.getSowingDate(), f.getPreviousCrop(),
                f.getBoundaryGeojson(), f.getCentroidLat(), f.getCentroidLon(),
                f.getCreatedAt(), f.getUpdatedAt());
    }
}
