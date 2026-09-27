package com.agrioptima.repository;

import com.agrioptima.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findAllByFieldIdOrderByCreatedAtDescIdDesc(Long fieldId);

    /** Ownership-scoped lookup through field -> farm -> owner. */
    @Query("""
            select r from Recommendation r
            join r.field f
            where r.id = :id and f.farm.owner.id = :ownerId
            """)
    Optional<Recommendation> findOwned(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
