-- Milestone: Field GIS polygon boundary and centroid coordinates
ALTER TABLE fields ADD COLUMN boundary_geojson LONGTEXT;
ALTER TABLE fields ADD COLUMN centroid_lat DECIMAL(10, 6);
ALTER TABLE fields ADD COLUMN centroid_lon DECIMAL(10, 6);
