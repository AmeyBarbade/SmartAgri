package com.agrioptima.service;

import com.agrioptima.dto.reference.CropResponse;
import com.agrioptima.dto.reference.FertilizerResponse;
import com.agrioptima.dto.reference.GrowthStageResponse;
import com.agrioptima.exception.ResourceNotFoundException;
import com.agrioptima.repository.CropGrowthStageRepository;
import com.agrioptima.repository.CropRepository;
import com.agrioptima.repository.FertilizerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Read-only access to Flyway-seeded crops, growth stages and fertilizers. */
@Service
@Transactional(readOnly = true)
public class ReferenceDataService {

    private final CropRepository cropRepository;
    private final CropGrowthStageRepository stageRepository;
    private final FertilizerRepository fertilizerRepository;

    public ReferenceDataService(CropRepository cropRepository, CropGrowthStageRepository stageRepository,
                                FertilizerRepository fertilizerRepository) {
        this.cropRepository = cropRepository;
        this.stageRepository = stageRepository;
        this.fertilizerRepository = fertilizerRepository;
    }

    public List<CropResponse> crops() {
        return cropRepository.findAllByOrderByNameAsc().stream().map(CropResponse::from).toList();
    }

    public List<GrowthStageResponse> stages(Long cropId) {
        if (!cropRepository.existsById(cropId)) {
            throw new ResourceNotFoundException("Crop", cropId);
        }
        return stageRepository.findAllByCropIdOrderBySeqAsc(cropId).stream().map(GrowthStageResponse::from).toList();
    }

    public List<FertilizerResponse> fertilizers() {
        return fertilizerRepository.findAllByActiveTrueOrderByNameAsc().stream().map(FertilizerResponse::from).toList();
    }
}
