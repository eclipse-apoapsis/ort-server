-- This migration script adds a missing ON DELETE CASCADE to the infrastructure_service_declarations_ort_runs table.
-- This is required to successfully delete ORT runs that have associated infrastructure service declarations.

ALTER TABLE infrastructure_service_declarations_ort_runs
  DROP CONSTRAINT infrastructure_service_declarations_ort_runs_ort_run_id_fkey,
  ADD CONSTRAINT infrastructure_service_declarations_ort_runs_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;
