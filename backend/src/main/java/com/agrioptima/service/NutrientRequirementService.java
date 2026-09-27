package com.agrioptima.service;

import com.agrioptima.dto.requirement.NutrientRequirementResponse;
import com.agrioptima.engine.EngineInputException;
import com.agrioptima.engine.NutrientRequirement;
import com.agrioptima.engine.NutrientRequirementEngine;
import com.agrioptima.engine.NutrientUnits;
import com.agrioptima.engine.RequirementInput;
import com.agrioptima.engine.RequirementInput.AppliedFertilizer;
import com.agrioptima.engine.RequirementInput.SoilTestInput;
import com.agrioptima.engine.RequirementInput.StageRef;
import com.agrioptima.engine.knowledge.KnowledgeBase;
import com.agrioptima.entity.Fertilizer;
import com.agrioptima.entity.Field;
import com.agrioptima.entity.IrrigationType;
import com.agrioptima.exception.InvalidRequestException;
import com.agrioptima.repository.CropGrowthStageRepository;
import com.agrioptima.repository.FertilizerApplicationRepository;
import com.agrioptima.repository.SoilRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Loads a field's crop, stage, latest soil test and previous applications, then runs the deterministic
 * {@link NutrientRequirementEngine}. Nothing is persisted; persistence of recommendations arrives with the
 * Spring Boot integration milestone.
 */
@Service
@Transactional(readOnly = true)
public class NutrientRequirementService {

    private final FieldService fieldService;
    private final CropGrowthStageRepository stageRepository;
    private final SoilRecordRepository soilRecordRepository;
    private final FertilizerApplicationRepository applicationRepository;
    private final NutrientRequirementEngine engine;
    private final Clock clock;

    public NutrientRequirementService(FieldService fieldService, CropGrowthStageRepository stageRepository,
                                      SoilRecordRepository soilRecordRepository,
                                      FertilizerApplicationRepository applicationRepository,
                                      NutrientRequirementEngine engine, Clock clock) {
        this.fieldService = fieldService;
        this.stageRepository = stageRepository;
        this.soilRecordRepository = soilRecordRepository;
        this.applicationRepository = applicationRepository;
        this.engine = engine;
        this.clock = clock;
    }

    public NutrientRequirementResponse forField(Long ownerId, Long fieldId, String profileCode) {
        Field field = fieldService.requireOwned(ownerId, fieldId);
        if (field.getCrop() == null) {
            throw new InvalidRequestException("Field " + fieldId + " has no crop; set a crop first.");
        }
        if (field.getGrowthStage() == null) {
            throw new InvalidRequestException("Field " + fieldId + " has no growth stage; set the current stage first.");
        }
        LocalDate asOf = LocalDate.now(clock);

        List<StageRef> stages = stageRepository.findAllByCropIdOrderBySeqAsc(field.getCrop().getId()).stream()
                .map(s -> new StageRef(s.getCode(), s.getName(), s.getSeq())).toList();
        SoilTestInput soil = soilRecordRepository.findFirstByFieldIdOrderBySampleDateDescIdDesc(fieldId)
                .map(s -> new SoilTestInput(s.getId(), s.getSampleDate(), s.getNitrogen().doubleValue(),
                        s.getPhosphorus().doubleValue(), s.getPotassium().doubleValue(), s.getPh().doubleValue(),
                        s.getOrganicCarbon() == null ? null : s.getOrganicCarbon().doubleValue()))
                .orElse(null);
        List<AppliedFertilizer> applications = applicationRepository.findAllForField(fieldId).stream()
                .map(a -> {
                    Fertilizer f = a.getFertilizer();
                    return new AppliedFertilizer(a.getId(), f.getCode(), a.getAppliedOn(),
                            NutrientUnits.nutrientsInProduct(a.getQuantityKg().doubleValue(),
                                    f.getNPct().doubleValue(), f.getP2o5Pct().doubleValue(),
                                    f.getK2oPct().doubleValue()));
                }).toList();
        Boolean rainfed = field.getIrrigationType() == null ? null : field.getIrrigationType() == IrrigationType.RAINFED;

        RequirementInput input = new RequirementInput(field.getCrop().getCode(), field.getGrowthStage().getCode(),
                profileCode, field.getAreaHa().doubleValue(), rainfed, field.getSowingDate(), asOf, soil,
                applications, stages);
        NutrientRequirement result;
        try {
            result = engine.calculate(input);
        } catch (EngineInputException e) {
            throw new InvalidRequestException(e.getMessage());
        }
        KnowledgeBase kb = engine.knowledgeBase();
        return NutrientRequirementResponse.from(field.getId(), field.getName(), asOf, result, kb.status(),
                kb.disclaimer());
    }

    public KnowledgeBase knowledgeBase() {
        return engine.knowledgeBase();
    }
}
