-- This migration script adds a reference to an environment to `evaluator_runs` which defaults to null as older runs do
-- not have it yet.

ALTER TABLE evaluator_runs
    ADD COLUMN environment_id bigint REFERENCES environments DEFAULT NULL;
