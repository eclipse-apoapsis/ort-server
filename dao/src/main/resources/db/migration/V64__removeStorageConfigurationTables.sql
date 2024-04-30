-- Remove scan storage related columns and tables, as the ORT Server uses a hardcoded storage configuration and did not
-- use these columns and tables.

ALTER TABLE scanner_configurations
    DROP COLUMN storage_readers,
    DROP COLUMN storage_writers;

-- Note that tables have to be dropped in the correct order to avoid issues with foreign key constraints.
DROP TABLE IF EXISTS file_archiver_configurations;
DROP TABLE IF EXISTS provenance_storage_configurations;
DROP TABLE IF EXISTS storage_configurations;
DROP TABLE IF EXISTS clearly_defined_storage_configurations;
DROP TABLE IF EXISTS sw360_storage_configurations;
DROP TABLE IF EXISTS scanner_configurations_storages;
DROP TABLE IF EXISTS file_based_storage_configurations;
DROP TABLE IF EXISTS file_storage_configurations;
DROP TABLE IF EXISTS local_file_storage_configurations;
DROP TABLE IF EXISTS http_file_storage_configuration_headers;
DROP TABLE IF EXISTS http_file_storage_configurations;
DROP TABLE IF EXISTS postgres_storage_configurations;
DROP TABLE IF EXISTS postgres_connections;
