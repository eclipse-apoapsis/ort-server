ALTER TABLE analyzer_configurations
    ADD COLUMN skip_excluded boolean NOT NULL DEFAULT false;

ALTER TABLE scanner_configurations
    ADD COLUMN skip_excluded boolean NOT NULL DEFAULT false;
