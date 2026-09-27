-- Milestone / Phase 1: Soil micronutrients (S, Zn, Fe, Cu, Mn, B) and Electrical Conductivity (EC)
ALTER TABLE soil_records ADD COLUMN sulfur DECIMAL(8,2) NULL;
ALTER TABLE soil_records ADD COLUMN zinc DECIMAL(8,2) NULL;
ALTER TABLE soil_records ADD COLUMN iron DECIMAL(8,2) NULL;
ALTER TABLE soil_records ADD COLUMN copper DECIMAL(8,2) NULL;
ALTER TABLE soil_records ADD COLUMN manganese DECIMAL(8,2) NULL;
ALTER TABLE soil_records ADD COLUMN boron DECIMAL(8,2) NULL;
ALTER TABLE soil_records ADD COLUMN ec DECIMAL(5,2) NULL;

ALTER TABLE soil_records ADD CONSTRAINT ck_soil_sulfur CHECK (sulfur IS NULL OR sulfur >= 0);
ALTER TABLE soil_records ADD CONSTRAINT ck_soil_zinc CHECK (zinc IS NULL OR zinc >= 0);
ALTER TABLE soil_records ADD CONSTRAINT ck_soil_iron CHECK (iron IS NULL OR iron >= 0);
ALTER TABLE soil_records ADD CONSTRAINT ck_soil_copper CHECK (copper IS NULL OR copper >= 0);
ALTER TABLE soil_records ADD CONSTRAINT ck_soil_manganese CHECK (manganese IS NULL OR manganese >= 0);
ALTER TABLE soil_records ADD CONSTRAINT ck_soil_boron CHECK (boron IS NULL OR boron >= 0);
ALTER TABLE soil_records ADD CONSTRAINT ck_soil_ec CHECK (ec IS NULL OR ec >= 0);
