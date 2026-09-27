package com.agrioptima.service;

import com.agrioptima.dto.application.FertilizerApplicationRequest;
import com.agrioptima.dto.application.FertilizerApplicationResponse;
import com.agrioptima.entity.CropGrowthStage;
import com.agrioptima.entity.Fertilizer;
import com.agrioptima.entity.FertilizerApplication;
import com.agrioptima.entity.Field;
import com.agrioptima.exception.InvalidRequestException;
import com.agrioptima.exception.ResourceNotFoundException;
import com.agrioptima.repository.CropGrowthStageRepository;
import com.agrioptima.repository.FertilizerApplicationRepository;
import com.agrioptima.repository.FertilizerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.agrioptima.service.FarmService.blankToNull;

/** Previous fertilizer usage per field; an input to the nutrient-requirement engine. */
@Service
@Transactional
public class FertilizerApplicationService {

    private final FertilizerApplicationRepository applicationRepository;
    private final FertilizerRepository fertilizerRepository;
    private final CropGrowthStageRepository stageRepository;
    private final FieldService fieldService;

    public FertilizerApplicationService(FertilizerApplicationRepository applicationRepository,
                                        FertilizerRepository fertilizerRepository,
                                        CropGrowthStageRepository stageRepository, FieldService fieldService) {
        this.applicationRepository = applicationRepository;
        this.fertilizerRepository = fertilizerRepository;
        this.stageRepository = stageRepository;
        this.fieldService = fieldService;
    }

    public FertilizerApplicationResponse create(Long ownerId, Long fieldId, FertilizerApplicationRequest r) {
        Field field = fieldService.requireOwned(ownerId, fieldId);
        Fertilizer fertilizer = fertilizerRepository.findById(r.fertilizerId())
                .orElseThrow(() -> new InvalidRequestException("Unknown fertilizerId " + r.fertilizerId() + "."));
        CropGrowthStage stage = null;
        if (r.growthStageId() != null) {
            stage = stageRepository.findById(r.growthStageId())
                    .orElseThrow(() -> new InvalidRequestException("Unknown growthStageId " + r.growthStageId() + "."));
            if (field.getCrop() == null || !stage.getCrop().getId().equals(field.getCrop().getId())) {
                throw new InvalidRequestException("Growth stage " + r.growthStageId()
                        + " does not belong to the crop of this field.");
            }
        }
        FertilizerApplication saved = applicationRepository.save(new FertilizerApplication(field, fertilizer, stage,
                r.appliedOn(), r.quantityKg(), blankToNull(r.notes())));
        return FertilizerApplicationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<FertilizerApplicationResponse> list(Long ownerId, Long fieldId) {
        fieldService.requireOwned(ownerId, fieldId);
        return applicationRepository.findAllForField(fieldId).stream().map(FertilizerApplicationResponse::from).toList();
    }

    public void delete(Long ownerId, Long applicationId) {
        FertilizerApplication a = applicationRepository.findOwned(applicationId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Fertilizer application", applicationId));
        applicationRepository.delete(a);
    }
}
