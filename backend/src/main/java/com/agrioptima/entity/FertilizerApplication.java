package com.agrioptima.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A fertilizer application already made on a field (previous usage). API arrives with the recommendation milestone. */
@Entity
@Table(name = "fertilizer_applications")
public class FertilizerApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id", nullable = false)
    private Field field;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fertilizer_id", nullable = false)
    private Fertilizer fertilizer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "growth_stage_id")
    private CropGrowthStage growthStage;

    @Column(name = "applied_on", nullable = false)
    private LocalDate appliedOn;

    /** Total product applied to the whole field, kg. */
    @Column(name = "quantity_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantityKg;

    @Column(length = 500)
    private String notes;

    protected FertilizerApplication() {
    }

    public FertilizerApplication(Field field, Fertilizer fertilizer, CropGrowthStage growthStage,
                                 LocalDate appliedOn, BigDecimal quantityKg, String notes) {
        this.field = field;
        this.fertilizer = fertilizer;
        this.growthStage = growthStage;
        this.appliedOn = appliedOn;
        this.quantityKg = quantityKg;
        this.notes = notes;
    }

    public Field getField() {
        return field;
    }

    public Fertilizer getFertilizer() {
        return fertilizer;
    }

    public CropGrowthStage getGrowthStage() {
        return growthStage;
    }

    public LocalDate getAppliedOn() {
        return appliedOn;
    }

    public BigDecimal getQuantityKg() {
        return quantityKg;
    }

    public String getNotes() {
        return notes;
    }
}
