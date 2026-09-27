package com.agrioptima.repository;

import com.agrioptima.entity.FertilizerApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FertilizerApplicationRepository extends JpaRepository<FertilizerApplication, Long> {

    List<FertilizerApplication> findAllByFieldIdOrderByAppliedOnDesc(Long fieldId);
}
