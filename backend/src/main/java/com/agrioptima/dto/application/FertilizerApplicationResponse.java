package com.agrioptima.dto.application;

import com.agrioptima.dto.requirement.Amounts;
import com.agrioptima.engine.NutrientUnits;
import com.agrioptima.entity.CropGrowthStage;
import com.agrioptima.entity.Fertilizer;
import com.agrioptima.entity.FertilizerApplication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** {@code nutrientsKg}: N, P2O5, K2O supplied to the whole field by this application (kg). */
public record FertilizerApplicationResponse(Long id, Long fieldId, FertilizerRef fertilizer, StageRef growthStage,
                                            LocalDate appliedOn, BigDecimal quantityKg, Amounts nutrientsKg,
                                            String notes, LocalDateTime createdAt) {

    public record FertilizerRef(Long id, String code, String name, BigDecimal nPct, BigDecimal p2o5Pct,
                                BigDecimal k2oPct) {
        static FertilizerRef of(Fertilizer f) {
            return new FertilizerRef(f.getId(), f.getCode(), f.getName(), f.getNPct(), f.getP2o5Pct(), f.getK2oPct());
        }
    }

    public record StageRef(Long id, String code, String name) {
        static StageRef of(CropGrowthStage s) {
            return s == null ? null : new StageRef(s.getId(), s.getCode(), s.getName());
        }
    }

    public static FertilizerApplicationResponse from(FertilizerApplication a) {
        Fertilizer f = a.getFertilizer();
        var nutrients = NutrientUnits.nutrientsInProduct(a.getQuantityKg().doubleValue(), f.getNPct().doubleValue(),
                f.getP2o5Pct().doubleValue(), f.getK2oPct().doubleValue());
        return new FertilizerApplicationResponse(a.getId(), a.getField().getId(), FertilizerRef.of(f),
                StageRef.of(a.getGrowthStage()), a.getAppliedOn(), a.getQuantityKg(), Amounts.of(nutrients),
                a.getNotes(), a.getCreatedAt());
    }
}
