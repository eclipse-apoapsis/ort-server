-- Table storing dynamic infrastructure services that are created from .ort.env.yml in the source code repository.
CREATE TABLE dynamic_infrastructure_services
(
     id bigserial PRIMARY KEY,
     name text NOT NULL,
     url text NOT NULL,
     credentials_type text,
     username_secret_name text NOT NULL,
     password_secret_name text NOT NULL
);

CREATE INDEX IF NOT EXISTS dynamic_infrastructure_services_name ON dynamic_infrastructure_services (name);

CREATE INDEX dynamic_infrastructure_services_all_value_columns
    ON dynamic_infrastructure_services (name, url, credentials_type, username_secret_name, password_secret_name);

-- Delete any dynamic infrastructure services that are already associated with an ORT run.
-- This does not matter, because they will be re-created from the .ort.env.yml on any new ORT run.
DROP TABLE IF EXISTS infrastructure_services_to_delete;
CREATE TEMP TABLE infrastructure_services_to_delete AS
    SELECT DISTINCT infrastructure_service_id
    FROM infrastructure_services_ort_runs;

DELETE FROM infrastructure_services_ort_runs
    WHERE infrastructure_service_id IN (SELECT infrastructure_service_id FROM infrastructure_services_to_delete);

DELETE FROM infrastructure_services
    WHERE id IN (SELECT infrastructure_service_id FROM infrastructure_services_to_delete);

DROP TABLE infrastructure_services_ort_runs;

CREATE TABLE dynamic_infrastructure_services_ort_runs
(
    dynamic_infrastructure_service_id bigint REFERENCES dynamic_infrastructure_services NOT NULL,
    ort_run_id bigint REFERENCES ort_runs NOT NULL,

    PRIMARY KEY (dynamic_infrastructure_service_id, ort_run_id)
);

