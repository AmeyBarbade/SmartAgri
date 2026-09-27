-- Reference data (Milestone 2).
--
-- CROPS / GROWTH STAGES: names and ordering of commonly used agronomic growth stages only.
-- No nutrient requirements, split percentages or stage timings are defined here; those are added in the
-- recommendation-engine milestone from documented sources.
--
-- FERTILIZERS: nutrient grades are the standard specifications for these products as listed in
-- India's Fertiliser (Control) Order, 1985 (Schedule I). Grades are % N, % P2O5, % K2O by weight.
--
-- PRICES: INDICATIVE PROTOTYPE VALUES (INR per kg), approximated from typical Indian retail bag prices.
-- They are NOT an official price list, vary by state/dealer/time, and are editable in the application.
--   Urea  ~ INR 266.50 / 45 kg bag  -> 5.92/kg
--   DAP   ~ INR 1350   / 50 kg bag  -> 27.00/kg
--   MOP   ~ INR 1700   / 50 kg bag  -> 34.00/kg
--   NPK 10:26:26 ~ INR 1470 / 50 kg bag -> 29.40/kg
--   SSP   ~ INR 550    / 50 kg bag  -> 11.00/kg

INSERT INTO crops (code, name, description, created_at, updated_at) VALUES
  ('RICE',  'Rice (Paddy)', 'Oryza sativa. Major kharif cereal.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('WHEAT', 'Wheat',        'Triticum aestivum. Major rabi cereal.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('MAIZE', 'Maize',        'Zea mays. Grown in kharif and rabi.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO crop_growth_stages (crop_id, code, name, seq, description, created_at, updated_at)
SELECT c.id, s.code, s.name, s.seq, s.description, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM crops c
JOIN (
  SELECT 'RICE' AS crop_code, 'ESTABLISHMENT' AS code, 'Sowing / transplanting' AS name, 1 AS seq, 'Crop establishment in the main field.' AS description
  UNION ALL SELECT 'RICE', 'TILLERING', 'Active tillering', 2, 'Vegetative phase in which tillers are produced.'
  UNION ALL SELECT 'RICE', 'PANICLE_INITIATION', 'Panicle initiation', 3, 'Start of the reproductive phase.'
  UNION ALL SELECT 'RICE', 'FLOWERING', 'Heading / flowering', 4, 'Panicle emergence and anthesis.'
  UNION ALL SELECT 'RICE', 'MATURITY', 'Grain filling / maturity', 5, 'Ripening phase.'
  UNION ALL SELECT 'WHEAT', 'SOWING', 'Sowing', 1, 'Crop establishment.'
  UNION ALL SELECT 'WHEAT', 'CRI', 'Crown root initiation', 2, 'Early vegetative stage when crown roots develop.'
  UNION ALL SELECT 'WHEAT', 'TILLERING', 'Tillering', 3, 'Vegetative phase in which tillers are produced.'
  UNION ALL SELECT 'WHEAT', 'JOINTING', 'Jointing', 4, 'Stem elongation.'
  UNION ALL SELECT 'WHEAT', 'FLOWERING', 'Heading / flowering', 5, 'Ear emergence and anthesis.'
  UNION ALL SELECT 'WHEAT', 'MATURITY', 'Grain filling / maturity', 6, 'Ripening phase.'
  UNION ALL SELECT 'MAIZE', 'SOWING', 'Sowing / emergence', 1, 'Crop establishment.'
  UNION ALL SELECT 'MAIZE', 'KNEE_HIGH', 'Knee-high (vegetative)', 2, 'Rapid vegetative growth.'
  UNION ALL SELECT 'MAIZE', 'TASSELING', 'Tasseling', 3, 'Tassel emergence.'
  UNION ALL SELECT 'MAIZE', 'SILKING', 'Silking', 4, 'Silk emergence and pollination.'
  UNION ALL SELECT 'MAIZE', 'MATURITY', 'Grain filling / maturity', 5, 'Ripening phase.'
) s ON s.crop_code = c.code;

INSERT INTO fertilizers (code, name, n_pct, p2o5_pct, k2o_pct, price_per_kg, bag_kg, source_ref, active, created_at, updated_at) VALUES
  ('UREA',      'Urea',                          46.00,  0.00,  0.00,  5.92, 45.00, 'Fertiliser (Control) Order 1985, Sch. I: Urea 46% N. Price indicative.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('DAP',       'Diammonium Phosphate (DAP)',    18.00, 46.00,  0.00, 27.00, 50.00, 'Fertiliser (Control) Order 1985, Sch. I: DAP 18-46-0. Price indicative.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('MOP',       'Muriate of Potash (MOP)',        0.00,  0.00, 60.00, 34.00, 50.00, 'Fertiliser (Control) Order 1985, Sch. I: MOP 60% K2O. Price indicative.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('NPK_10_26_26', 'NPK Complex 10:26:26',       10.00, 26.00, 26.00, 29.40, 50.00, 'Fertiliser (Control) Order 1985, Sch. I: NPK 10-26-26. Price indicative.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('SSP',       'Single Super Phosphate (SSP)',   0.00, 16.00,  0.00, 11.00, 50.00, 'Fertiliser (Control) Order 1985, Sch. I: SSP 16% water-soluble P2O5. Price indicative.', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
