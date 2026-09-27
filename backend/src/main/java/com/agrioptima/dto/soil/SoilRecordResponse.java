package com.agrioptima.dto.soil;

import com.agrioptima.entity.SoilRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SoilRecordResponse(Long id, Long fieldId, LocalDate sampleDate, BigDecimal nitrogen,
                                 BigDecimal phosphorus, BigDecimal potassium, BigDecimal ph,
                                 BigDecimal organicCarbon, BigDecimal moisture,
                                 BigDecimal sulfur, BigDecimal zinc, BigDecimal iron,
                                 BigDecimal copper, BigDecimal manganese, BigDecimal boron,
                                 BigDecimal ec, String notes, LocalDateTime createdAt) {

    public static SoilRecordResponse from(SoilRecord s) {
        return new SoilRecordResponse(s.getId(), s.getField().getId(), s.getSampleDate(), s.getNitrogen(),
                s.getPhosphorus(), s.getPotassium(), s.getPh(), s.getOrganicCarbon(), s.getMoisture(),
                s.getSulfur(), s.getZinc(), s.getIron(), s.getCopper(), s.getManganese(), s.getBoron(),
                s.getEc(), s.getNotes(), s.getCreatedAt());
    }
}
