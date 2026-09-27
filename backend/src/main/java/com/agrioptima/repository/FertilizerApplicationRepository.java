package com.agrioptima.repository;

import com.agrioptima.entity.FertilizerApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FertilizerApplicationRepository extends JpaRepository<FertilizerApplication, Long> {

    @Query("""
            select a from FertilizerApplication a
            join fetch a.fertilizer
            left join fetch a.growthStage
            where a.field.id = :fieldId
            order by a.appliedOn desc, a.id desc
            """)
    List<FertilizerApplication> findAllForField(@Param("fieldId") Long fieldId);

    /** Ownership-scoped lookup through application -> field -> farm -> owner. */
    @Query("""
            select a from FertilizerApplication a
            join fetch a.field f
            join fetch a.fertilizer
            left join fetch a.growthStage
            where a.id = :id and f.farm.owner.id = :ownerId
            """)
    Optional<FertilizerApplication> findOwned(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
