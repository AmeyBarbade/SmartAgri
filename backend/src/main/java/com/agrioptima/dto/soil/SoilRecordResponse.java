package com.agrioptima.dto.soil;

import com.agrioptima.entity.SoilRecord;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SoilRecordResponse(Long id, Long fieldId, LocalDate sampleDate, BigDecimal nitrogen,
                                 BigDecimal phosphorus, BigDecimal potassium, BigDecimal ph,
                                 BigDecimal organicCarbon, BigDecimal moisture, String notes,
                                 LocalDateTime createdAt) {

    public static SoilRecordResponse from(SoilRecord s) {
        return new SoilRecordResponse(s.getId(), s.getField().getId(), s.getSampleDate(), s.getNitrogen(),
                s.getPhosphorus(), s.getPotassium(), s.getPh(), s.getOrganicCarbon(), s.getMoisture(),
                s.getNotes(), s.getCreatedAt());
    }
}
