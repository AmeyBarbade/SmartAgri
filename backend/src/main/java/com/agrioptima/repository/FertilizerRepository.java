package com.agrioptima.repository;

import com.agrioptima.entity.Fertilizer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FertilizerRepository extends JpaRepository<Fertilizer, Long> {

    List<Fertilizer> findAllByActiveTrueOrderByNameAsc();
}
