package com.agrioptima.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Summary of one candidate plan of a recommendation (details are in the parent's response JSON). */
@Entity
@Table(name = "recommendation_plans")
public class RecommendationPlan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private Recommendation recommendation;

    @Column(nullable = false, length = 20)
    private String strategy;

    @Column(name = "cost_per_ha", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPerHa;

    @Column(name = "field_cost", nullable = false, precision = 16, scale = 2)
    private BigDecimal fieldCost;

    @Column(name = "total_excess_kg_ha", nullable = false, precision = 10, scale = 3)
    private BigDecimal totalExcessKgHa;

    @Column(name = "total_mass_kg_ha", nullable = false, precision = 10, scale = 3)
    private BigDecimal totalMassKgHa;

    @Column(name = "predicted_yield_t_ha", precision = 6, scale = 3)
    private BigDecimal predictedYieldTHa;

    @Column(name = "score_per_ha", nullable = false, precision = 14, scale = 2)
    private BigDecimal scorePerHa;

    @Column(nullable = false)
    private boolean selected;

    protected RecommendationPlan() {
    }

    public RecommendationPlan(Recommendation recommendation, String strategy, BigDecimal costPerHa,
                              BigDecimal fieldCost, BigDecimal totalExcessKgHa, BigDecimal totalMassKgHa,
                              BigDecimal predictedYieldTHa, BigDecimal scorePerHa, boolean selected) {
        this.recommendation = recommendation;
        this.strategy = strategy;
        this.costPerHa = costPerHa;
        this.fieldCost = fieldCost;
        this.totalExcessKgHa = totalExcessKgHa;
        this.totalMassKgHa = totalMassKgHa;
        this.predictedYieldTHa = predictedYieldTHa;
        this.scorePerHa = scorePerHa;
        this.selected = selected;
    }

    public String getStrategy() {
        return strategy;
    }

    public BigDecimal getPredictedYieldTHa() {
        return predictedYieldTHa;
    }

    public boolean isSelected() {
        return selected;
    }
}
