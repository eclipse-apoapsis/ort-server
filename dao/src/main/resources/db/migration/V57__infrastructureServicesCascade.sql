ALTER TABLE infrastructure_services_ort_runs
    DROP CONSTRAINT IF EXISTS infrastructure_services_ort_runs_infrastructure_service_id_fkey;

ALTER TABLE infrastructure_services_ort_runs
    ADD CONSTRAINT infrastructure_services_ort_runs_infrastructure_service_id_fkey
    FOREIGN KEY (infrastructure_service_id)
    REFERENCES infrastructure_services(id)
    ON DELETE CASCADE;
