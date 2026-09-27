package com.agrioptima.repository;

import com.agrioptima.entity.CropGrowthStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CropGrowthStageRepository extends JpaRepository<CropGrowthStage, Long> {

    List<CropGrowthStage> findAllByCropIdOrderBySeqAsc(Long cropId);
}
