-- AgriOptima foundation schema (Milestone 2).
-- Portable across MySQL 8.0.16+ (CHECK constraints enforced) and H2 in MySQL mode.
-- Units: soil available N/P/K in kg/ha; fertilizer grade in % N, % P2O5, % K2O.
-- Recommendation / plan tables arrive with the recommendation milestone.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE farms (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id      BIGINT       NOT NULL,
    name          VARCHAR(100) NOT NULL,
    location_name VARCHAR(150),
    latitude      DECIMAL(9,6),
    longitude     DECIMAL(9,6),
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_farms PRIMARY KEY (id),
    CONSTRAINT fk_farms_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_farms_latitude CHECK (latitude IS NULL OR (latitude BETWEEN -90 AND 90)),
    CONSTRAINT ck_farms_longitude CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180))
);
CREATE INDEX idx_farms_owner ON farms (owner_id);

CREATE TABLE crops (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_crops PRIMARY KEY (id),
    CONSTRAINT uk_crops_code UNIQUE (code)
);

CREATE TABLE crop_growth_stages (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    crop_id     BIGINT       NOT NULL,
    code        VARCHAR(40)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    seq         INT          NOT NULL,
    description VARCHAR(500),
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_crop_growth_stages PRIMARY KEY (id),
    CONSTRAINT fk_stages_crop FOREIGN KEY (crop_id) REFERENCES crops (id) ON DELETE CASCADE,
    CONSTRAINT uk_stages_crop_code UNIQUE (crop_id, code),
    CONSTRAINT uk_stages_crop_seq UNIQUE (crop_id, seq),
    CONSTRAINT ck_stages_seq CHECK (seq > 0)
);

CREATE TABLE fields (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    farm_id         BIGINT        NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    area_ha         DECIMAL(10,3) NOT NULL,
    soil_type       VARCHAR(40),
    irrigation_type VARCHAR(20),
    crop_id         BIGINT,
    growth_stage_id BIGINT,
    season          VARCHAR(20),
    sowing_date     DATE,
    previous_crop   VARCHAR(100),
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT pk_fields PRIMARY KEY (id),
    CONSTRAINT fk_fields_farm FOREIGN KEY (farm_id) REFERENCES farms (id) ON DELETE CASCADE,
    CONSTRAINT fk_fields_crop FOREIGN KEY (crop_id) REFERENCES crops (id),
    CONSTRAINT fk_fields_stage FOREIGN KEY (growth_stage_id) REFERENCES crop_growth_stages (id),
    CONSTRAINT ck_fields_area CHECK (area_ha > 0)
);
CREATE INDEX idx_fields_farm ON fields (farm_id);

CREATE TABLE soil_records (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    field_id        BIGINT       NOT NULL,
    sample_date     DATE         NOT NULL,
    nitrogen        DECIMAL(8,2) NOT NULL,
    phosphorus      DECIMAL(8,2) NOT NULL,
    potassium       DECIMAL(8,2) NOT NULL,
    ph              DECIMAL(4,2) NOT NULL,
    organic_carbon  DECIMAL(5,2),
    moisture        DECIMAL(5,2),
    notes           VARCHAR(500),
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_soil_records PRIMARY KEY (id),
    CONSTRAINT fk_soil_field FOREIGN KEY (field_id) REFERENCES fields (id) ON DELETE CASCADE,
    CONSTRAINT ck_soil_npk CHECK (nitrogen >= 0 AND phosphorus >= 0 AND potassium >= 0),
    CONSTRAINT ck_soil_ph CHECK (ph BETWEEN 0 AND 14),
    CONSTRAINT ck_soil_oc CHECK (organic_carbon IS NULL OR (organic_carbon BETWEEN 0 AND 100)),
    CONSTRAINT ck_soil_moisture CHECK (moisture IS NULL OR (moisture BETWEEN 0 AND 100))
);
CREATE INDEX idx_soil_field_date ON soil_records (field_id, sample_date);

CREATE TABLE fertilizers (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    code         VARCHAR(40)   NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    n_pct        DECIMAL(5,2)  NOT NULL,
    p2o5_pct     DECIMAL(5,2)  NOT NULL,
    k2o_pct      DECIMAL(5,2)  NOT NULL,
    price_per_kg DECIMAL(10,2) NOT NULL,
    bag_kg       DECIMAL(6,2),
    source_ref   VARCHAR(500),
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    CONSTRAINT pk_fertilizers PRIMARY KEY (id),
    CONSTRAINT uk_fertilizers_code UNIQUE (code),
    CONSTRAINT ck_fert_pct CHECK (n_pct BETWEEN 0 AND 100 AND p2o5_pct BETWEEN 0 AND 100 AND k2o_pct BETWEEN 0 AND 100),
    CONSTRAINT ck_fert_price CHECK (price_per_kg >= 0)
);

CREATE TABLE fertilizer_applications (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    field_id         BIGINT        NOT NULL,
    fertilizer_id    BIGINT        NOT NULL,
    growth_stage_id  BIGINT,
    applied_on       DATE          NOT NULL,
    quantity_kg      DECIMAL(10,2) NOT NULL,
    notes            VARCHAR(500),
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    CONSTRAINT pk_fertilizer_applications PRIMARY KEY (id),
    CONSTRAINT fk_app_field FOREIGN KEY (field_id) REFERENCES fields (id) ON DELETE CASCADE,
    CONSTRAINT fk_app_fertilizer FOREIGN KEY (fertilizer_id) REFERENCES fertilizers (id),
    CONSTRAINT fk_app_stage FOREIGN KEY (growth_stage_id) REFERENCES crop_growth_stages (id),
    CONSTRAINT ck_app_qty CHECK (quantity_kg > 0)
);
CREATE INDEX idx_app_field_date ON fertilizer_applications (field_id, applied_on);
