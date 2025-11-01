ALTER TABLE ort_runs
    ADD COLUMN outdated         boolean DEFAULT FALSE   NOT NULL,
    ADD COLUMN outdated_message text                    NULL;
