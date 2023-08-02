ALTER TABLE ort_runs
    ADD COLUMN config_context text NULL;

ALTER TABLE ort_runs
    ADD COLUMN resolved_config_context text NULL;
