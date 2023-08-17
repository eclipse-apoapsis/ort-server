ALTER TABLE ort_runs
    RENAME COLUMN config TO job_configs;

ALTER TABLE ort_runs
    RENAME COLUMN resolved_config TO resolved_job_configs;

ALTER TABLE ort_runs
    RENAME COLUMN config_context TO job_config_context;

ALTER TABLE ort_runs
    RENAME COLUMN resolved_config_context TO resolved_job_config_context;
