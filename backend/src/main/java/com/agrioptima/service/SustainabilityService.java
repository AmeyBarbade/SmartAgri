package com.agrioptima.service;

import com.agrioptima.dto.sustainability.SustainabilityResponse;
import com.agrioptima.dto.sustainability.SustainabilityResponse.SeasonDataPoint;
import com.agrioptima.entity.Field;
import com.agrioptima.entity.SoilRecord;
import com.agrioptima.repository.FieldRepository;
import com.agrioptima.repository.SoilRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SustainabilityService {

    private final FieldService fieldService;
    private final SoilRecordRepository soilRecordRepository;

    public SustainabilityService(FieldService fieldService, SoilRecordRepository soilRecordRepository) {
        this.fieldService = fieldService;
        this.soilRecordRepository = soilRecordRepository;
    }

    public SustainabilityResponse getFieldSustainability(Long ownerId, Long fieldId) {
        Field field = fieldService.requireOwned(ownerId, fieldId);
        SoilRecord soil = soilRecordRepository.findFirstByFieldIdOrderBySampleDateDescIdDesc(fieldId).orElse(null);

        double baseOc = soil != null && soil.getOrganicCarbon() != null
                ? soil.getOrganicCarbon().doubleValue()
                : 0.45;
        double basePh = soil != null && soil.getPh() != null
                ? soil.getPh().doubleValue()
                : 7.2;

        int healthScore = 50;
        if (baseOc >= 0.75) healthScore += 25;
        else if (baseOc >= 0.50) healthScore += 12;
        else if (baseOc < 0.35) healthScore -= 15;

        if (basePh >= 6.5 && basePh <= 7.8) healthScore += 15;
        else if (basePh >= 6.0 && basePh <= 8.5) healthScore += 5;
        else healthScore -= 10;

        healthScore = Math.max(25, Math.min(95, healthScore));

        String rating;
        if (healthScore >= 75) rating = "Optimal Agro-Ecosystem";
        else if (healthScore >= 60) rating = "Good Soil Health";
        else if (healthScore >= 45) rating = "Moderate (Needs Organic Matter)";
        else rating = "Degraded / Low Organic Carbon";

        double targetOc = Math.round((baseOc + 0.28) * 100.0) / 100.0;
        double chemReduction = 25.0; // 25% IPNS substitution target
        double annualSavings = Math.round((field.getAreaHa().doubleValue() * 3850.0) * 100.0) / 100.0;

        // Build 6-season timeline
        List<SeasonDataPoint> timeline = new ArrayList<>();
        String[] seasonLabels = {
                "Season 1 (Baseline)",
                "Season 2 (IPNS Adoption)",
                "Season 3 (Active Regeneration)",
                "Season 4 (Humus Accumulation)",
                "Season 5 (Microbial Equilibrium)",
                "Season 6 (Target State)"
        };

        double currOc = baseOc;
        double currChemN = 120.0;
        double currOrgN = 0.0;
        double currCost = 7200.0;
        double currYield = 4.80;

        for (int i = 0; i < seasonLabels.length; i++) {
            boolean historical = (i == 0);
            if (i > 0) {
                currOc = Math.round((currOc + 0.055) * 1000.0) / 1000.0;
                currChemN = Math.round((currChemN - 7.5) * 10.0) / 10.0;
                currOrgN = Math.round((currOrgN + 6.0) * 10.0) / 10.0;
                currCost = Math.round((currCost - 480.0) * 10.0) / 10.0;
                currYield = Math.round((currYield + 0.12) * 100.0) / 100.0;
            }

            timeline.add(new SeasonDataPoint(
                    seasonLabels[i],
                    BigDecimal.valueOf(currOc).setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(currChemN).setScale(1, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(currOrgN).setScale(1, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(currCost).setScale(1, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(currYield).setScale(2, RoundingMode.HALF_UP),
                    historical
            ));
        }

        List<String> insights = List.of(
                "Adopting 25% Integrated Plant Nutrition System (IPNS) replaces synthetic urea with Farm Yard Manure (FYM) or Vermicompost.",
                "Soil Organic Carbon increases from " + baseOc + "% to " + targetOc + "%, boosting microbial biomass and cation exchange capacity.",
                "Projected ₹" + annualSavings + " total fertilizer expenditure saved over the field area (" + field.getAreaHa() + " ha).",
                "Gradual organic enrichment buffers against soil acidification and improves root-zone water retention by ~18%."
        );

        return new SustainabilityResponse(
                field.getId(),
                field.getName(),
                field.getFarm().getName(),
                field.getCrop() != null ? field.getCrop().getName() : "General Crop",
                field.getAreaHa(),
                healthScore,
                rating,
                BigDecimal.valueOf(baseOc).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(targetOc).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(chemReduction).setScale(1, RoundingMode.HALF_UP),
                BigDecimal.valueOf(annualSavings).setScale(2, RoundingMode.HALF_UP),
                timeline,
                insights
        );
    }
}
