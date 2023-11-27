-- Drop unique constraints as multiple entries are now allowed.
ALTER TABLE ort_runs
    ADD COLUMN finished_at timestamp NULL;
