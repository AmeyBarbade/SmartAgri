-- Milestone 7: persisted recommendation runs (architecture §5, recommendations + recommendation_plans).
-- A run stores the key figures as columns (for lists and later dashboards) and the complete API response as JSON,
-- so a stored recommendation is shown exactly as it was computed, even after reference data or models change.
-- Plan line items live only in response_json for now (no query needs them yet).

CREATE TABLE recommendations (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    field_id           BIGINT        NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    feasible           BOOLEAN       NOT NULL,
    crop_code          VARCHAR(20)   NOT NULL,
    stage_code         VARCHAR(40)   NOT NULL,
    area_ha            DECIMAL(10,3) NOT NULL,
    required_n         DECIMAL(10,2) NOT NULL,
    required_p2o5      DECIMAL(10,2) NOT NULL,
    required_k2o       DECIMAL(10,2) NOT NULL,
    selected_strategy  VARCHAR(20),
    scoring_mode       VARCHAR(30)   NOT NULL,
    kb_version         VARCHAR(20)   NOT NULL,
    model_version      VARCHAR(80),
    response_json      LONGTEXT      NOT NULL,
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    CONSTRAINT pk_recommendations PRIMARY KEY (id),
    CONSTRAINT fk_rec_field FOREIGN KEY (field_id) REFERENCES fields (id) ON DELETE CASCADE,
    CONSTRAINT ck_rec_status CHECK (status IN ('OPTIMAL', 'NOTHING_REQUIRED', 'INFEASIBLE'))
);
CREATE INDEX idx_rec_field_created ON recommendations (field_id, created_at);

CREATE TABLE recommendation_plans (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    recommendation_id     BIGINT        NOT NULL,
    strategy              VARCHAR(20)   NOT NULL,
    cost_per_ha           DECIMAL(12,2) NOT NULL,
    field_cost            DECIMAL(16,2) NOT NULL,
    total_excess_kg_ha    DECIMAL(10,3) NOT NULL,
    total_mass_kg_ha      DECIMAL(10,3) NOT NULL,
    predicted_yield_t_ha  DECIMAL(6,3),
    score_per_ha          DECIMAL(14,2) NOT NULL,
    selected              BOOLEAN       NOT NULL,
    created_at            DATETIME(6)   NOT NULL,
    updated_at            DATETIME(6)   NOT NULL,
    CONSTRAINT pk_recommendation_plans PRIMARY KEY (id),
    CONSTRAINT fk_recplan_rec FOREIGN KEY (recommendation_id) REFERENCES recommendations (id) ON DELETE CASCADE,
    CONSTRAINT uq_recplan_strategy UNIQUE (recommendation_id, strategy)
);
