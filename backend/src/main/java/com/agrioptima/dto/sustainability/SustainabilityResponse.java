package com.agrioptima.dto.sustainability;

import java.math.BigDecimal;
import java.util.List;

public record SustainabilityResponse(
        Long fieldId,
        String fieldName,
        String farmName,
        String cropName,
        BigDecimal areaHa,
        int soilHealthScore,
        String soilHealthRating,
        BigDecimal currentOrganicCarbon,
        BigDecimal targetOrganicCarbon,
        BigDecimal chemicalReductionPct,
        BigDecimal projectedAnnualSavingsInr,
        List<SeasonDataPoint> timeline,
        List<String> insights
) {
    public record SeasonDataPoint(
            String seasonLabel,
            BigDecimal organicCarbonPct,
            BigDecimal chemicalNitrogenKgHa,
            BigDecimal organicNitrogenKgHa,
            BigDecimal fertilizerCostInrHa,
            BigDecimal yieldTHa,
            boolean historical
    ) {}
}
