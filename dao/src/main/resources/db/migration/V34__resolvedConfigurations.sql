ALTER TABLE ort_runs
    RENAME COLUMN job_configurations TO config;

ALTER TABLE ort_runs
    ADD COLUMN      resolved_config      jsonb        NULL;
