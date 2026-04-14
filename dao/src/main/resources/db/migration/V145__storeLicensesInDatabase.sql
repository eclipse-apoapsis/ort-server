ALTER TABLE packages
    ADD COLUMN detected_licenses TEXT,
    ADD COLUMN effective_license TEXT;

ALTER TABLE projects
    ADD COLUMN detected_licenses TEXT,
    ADD COLUMN effective_license TEXT;