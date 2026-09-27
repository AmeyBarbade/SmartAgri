package com.agrioptima.repository;

import com.agrioptima.entity.SoilRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SoilRecordRepository extends JpaRepository<SoilRecord, Long> {

    List<SoilRecord> findAllByFieldIdOrderBySampleDateDescIdDesc(Long fieldId);

    Optional<SoilRecord> findFirstByFieldIdOrderBySampleDateDescIdDesc(Long fieldId);

    /** Ownership-scoped lookup through field -> farm -> owner. */
    @Query("""
            select s from SoilRecord s
            join fetch s.field f
            where s.id = :id and f.farm.owner.id = :ownerId
            """)
    Optional<SoilRecord> findOwned(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
