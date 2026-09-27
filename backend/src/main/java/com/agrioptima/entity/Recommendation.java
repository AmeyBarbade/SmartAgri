package com.agrioptima.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** One recommendation run for a field: key figures as columns, the full API response as JSON. */
@Entity
@Table(name = "recommendations")
public class Recommendation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id", nullable = false)
    private Field field;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private boolean feasible;

    @Column(name = "crop_code", nullable = false, length = 20)
    private String cropCode;

    @Column(name = "stage_code", nullable = false, length = 40)
    private String stageCode;

    @Column(name = "area_ha", nullable = false, precision = 10, scale = 3)
    private BigDecimal areaHa;

    @Column(name = "required_n", nullable = false, precision = 10, scale = 2)
    private BigDecimal requiredN;

    @Column(name = "required_p2o5", nullable = false, precision = 10, scale = 2)
    private BigDecimal requiredP2o5;

    @Column(name = "required_k2o", nullable = false, precision = 10, scale = 2)
    private BigDecimal requiredK2o;

    @Column(name = "selected_strategy", length = 20)
    private String selectedStrategy;

    @Column(name = "scoring_mode", nullable = false, length = 30)
    private String scoringMode;

    @Column(name = "kb_version", nullable = false, length = 20)
    private String kbVersion;

    @Column(name = "model_version", length = 80)
    private String modelVersion;

    @Column(name = "response_json", nullable = false, columnDefinition = "LONGTEXT")
    private String responseJson;

    @OneToMany(mappedBy = "recommendation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<RecommendationPlan> plans = new ArrayList<>();

    protected Recommendation() {
    }

    public Recommendation(Field field, String status, boolean feasible, String cropCode, String stageCode,
                          BigDecimal areaHa, BigDecimal requiredN, BigDecimal requiredP2o5, BigDecimal requiredK2o,
                          String selectedStrategy, String scoringMode, String kbVersion, String modelVersion,
                          String responseJson) {
        this.field = field;
        this.status = status;
        this.feasible = feasible;
        this.cropCode = cropCode;
        this.stageCode = stageCode;
        this.areaHa = areaHa;
        this.requiredN = requiredN;
        this.requiredP2o5 = requiredP2o5;
        this.requiredK2o = requiredK2o;
        this.selectedStrategy = selectedStrategy;
        this.scoringMode = scoringMode;
        this.kbVersion = kbVersion;
        this.modelVersion = modelVersion;
        this.responseJson = responseJson;
    }

    public void addPlan(RecommendationPlan plan) {
        plans.add(plan);
    }

    public Field getField() {
        return field;
    }

    public String getStatus() {
        return status;
    }

    public boolean isFeasible() {
        return feasible;
    }

    public String getCropCode() {
        return cropCode;
    }

    public String getStageCode() {
        return stageCode;
    }

    public String getScoringMode() {
        return scoringMode;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getSelectedStrategy() {
        return selectedStrategy;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public List<RecommendationPlan> getPlans() {
        return plans;
    }
}
