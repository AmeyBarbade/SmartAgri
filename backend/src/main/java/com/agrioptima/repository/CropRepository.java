package com.agrioptima.repository;

import com.agrioptima.entity.Crop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CropRepository extends JpaRepository<Crop, Long> {

    List<Crop> findAllByOrderByNameAsc();
}
