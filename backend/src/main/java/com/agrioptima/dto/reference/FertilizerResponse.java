package com.agrioptima.dto.reference;

import com.agrioptima.entity.Fertilizer;

import java.math.BigDecimal;

/** Grade in % N, % P2O5, % K2O; price in INR/kg (indicative). */
public record FertilizerResponse(Long id, String code, String name, BigDecimal nPct, BigDecimal p2o5Pct,
                                 BigDecimal k2oPct, BigDecimal pricePerKg, BigDecimal bagKg, String sourceRef) {

    public static FertilizerResponse from(Fertilizer f) {
        return new FertilizerResponse(f.getId(), f.getCode(), f.getName(), f.getNPct(), f.getP2o5Pct(),
                f.getK2oPct(), f.getPricePerKg(), f.getBagKg(), f.getSourceRef());
    }
}
