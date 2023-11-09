-- Make columns nullable which need not to be set at creation time.
ALTER TABLE scanner_runs
    ALTER COLUMN environment_id DROP NOT NULL,
    ALTER COLUMN start_time DROP NOT NULL,
    ALTER COLUMN end_time DROP NOT NULL;
