package com.agrioptima.service;

import com.agrioptima.dto.field.FieldRequest;
import com.agrioptima.dto.field.FieldResponse;
import com.agrioptima.entity.Crop;
import com.agrioptima.entity.CropGrowthStage;
import com.agrioptima.entity.Farm;
import com.agrioptima.entity.Field;
import com.agrioptima.exception.InvalidRequestException;
import com.agrioptima.exception.ResourceNotFoundException;
import com.agrioptima.repository.CropGrowthStageRepository;
import com.agrioptima.repository.CropRepository;
import com.agrioptima.repository.FieldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.agrioptima.service.FarmService.blankToNull;

/** Field CRUD within a farm. Ownership is checked through field -> farm -> owner. */
@Service
@Transactional
public class FieldService {

    private final FieldRepository fieldRepository;
    private final FarmService farmService;
    private final CropRepository cropRepository;
    private final CropGrowthStageRepository stageRepository;

    public FieldService(FieldRepository fieldRepository, FarmService farmService, CropRepository cropRepository,
                        CropGrowthStageRepository stageRepository) {
        this.fieldRepository = fieldRepository;
        this.farmService = farmService;
        this.cropRepository = cropRepository;
        this.stageRepository = stageRepository;
    }

    @Transactional(readOnly = true)
    public List<FieldResponse> listForFarm(Long ownerId, Long farmId) {
        farmService.requireOwned(ownerId, farmId);
        return fieldRepository.findAllByFarmId(farmId).stream().map(FieldResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FieldResponse get(Long ownerId, Long fieldId) {
        return FieldResponse.from(requireOwned(ownerId, fieldId));
    }

    public FieldResponse create(Long ownerId, Long farmId, FieldRequest request) {
        Farm farm = farmService.requireOwned(ownerId, farmId);
        Field field = new Field(farm);
        apply(field, request);
        return FieldResponse.from(fieldRepository.save(field));
    }

    public FieldResponse update(Long ownerId, Long fieldId, FieldRequest request) {
        Field field = requireOwned(ownerId, fieldId);
        apply(field, request);
        return FieldResponse.from(fieldRepository.saveAndFlush(field));
    }

    /** Deletes the field; its soil records and applications are removed by ON DELETE CASCADE. */
    public void delete(Long ownerId, Long fieldId) {
        fieldRepository.delete(requireOwned(ownerId, fieldId));
    }

    /** @throws ResourceNotFoundException if the field does not exist or belongs to another user */
    public Field requireOwned(Long ownerId, Long fieldId) {
        return fieldRepository.findOwned(fieldId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Field", fieldId));
    }

    private void apply(Field field, FieldRequest r) {
        Crop crop = null;
        CropGrowthStage stage = null;
        if (r.cropId() != null) {
            crop = cropRepository.findById(r.cropId())
                    .orElseThrow(() -> new InvalidRequestException("Unknown cropId " + r.cropId() + "."));
        }
        if (r.growthStageId() != null) {
            stage = stageRepository.findById(r.growthStageId())
                    .orElseThrow(() -> new InvalidRequestException("Unknown growthStageId " + r.growthStageId() + "."));
            if (crop == null || !stage.getCrop().getId().equals(crop.getId())) {
                throw new InvalidRequestException("Growth stage " + r.growthStageId()
                        + " does not belong to crop " + r.cropId() + ".");
            }
        }
        field.setName(r.name().trim());
        field.setAreaHa(r.areaHa());
        field.setSoilType(blankToNull(r.soilType()));
        field.setIrrigationType(r.irrigationType());
        field.setCrop(crop);
        field.setGrowthStage(stage);
        field.setSeason(r.season());
        field.setSowingDate(r.sowingDate());
        field.setPreviousCrop(blankToNull(r.previousCrop()));
    }
}
