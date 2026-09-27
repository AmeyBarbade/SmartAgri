package com.agrioptima.repository;

import com.agrioptima.entity.Field;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FieldRepository extends JpaRepository<Field, Long> {

    @Query("""
            select f from Field f
            left join fetch f.crop
            left join fetch f.growthStage
            where f.farm.id = :farmId
            order by f.name asc
            """)
    List<Field> findAllByFarmId(@Param("farmId") Long farmId);

    /** Ownership-scoped lookup: returns empty when the field exists but belongs to another user. */
    @Query("""
            select f from Field f
            join fetch f.farm fa
            left join fetch f.crop
            left join fetch f.growthStage
            where f.id = :id and fa.owner.id = :ownerId
            """)
    Optional<Field> findOwned(@Param("id") Long id, @Param("ownerId") Long ownerId);

    long countByFarmId(Long farmId);
}
