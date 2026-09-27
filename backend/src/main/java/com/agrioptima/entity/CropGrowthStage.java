package com.agrioptima.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "crop_growth_stages")
public class CropGrowthStage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "crop_id", nullable = false)
    private Crop crop;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** 1-based position of the stage in the crop's life cycle. */
    @Column(nullable = false)
    private int seq;

    @Column(length = 500)
    private String description;

    protected CropGrowthStage() {
    }

    public Crop getCrop() {
        return crop;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getSeq() {
        return seq;
    }

    public String getDescription() {
        return description;
    }
}
