-- Table storing infrastructure service declarations that are created from .ort.env.yml in the source code repository.
CREATE TABLE infrastructure_service_declarations
(
     id bigserial PRIMARY KEY,
     name text NOT NULL,
     url text NOT NULL,
     description text,
     credentials_type text,
     username_secret text NOT NULL,
     password_secret text NOT NULL
);

CREATE INDEX IF NOT EXISTS infrastructure_service_declarations_name ON infrastructure_service_declarations (name);

CREATE INDEX infrastructure_service_declarations_all_value_columns
    ON infrastructure_service_declarations (name, url, description, credentials_type, username_secret, password_secret);

-- Delete any infrastructure services that are already associated with an ORT run.
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

CREATE TABLE infrastructure_service_declarations_ort_runs
(
    infrastructure_service_declaration_id bigint REFERENCES infrastructure_service_declarations NOT NULL,
    ort_run_id bigint REFERENCES ort_runs NOT NULL,

    PRIMARY KEY (infrastructure_service_declaration_id, ort_run_id)
);
