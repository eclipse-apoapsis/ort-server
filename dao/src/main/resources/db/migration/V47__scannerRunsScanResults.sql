-- Drop unique constraints as multiple entries are now allowed.
ALTER TABLE scan_results
    DROP CONSTRAINT scan_results_artifact_url_artifact_hash_artifact_hash_algor_key,
    DROP CONSTRAINT scan_results_vcs_type_vcs_url_vcs_revision_scanner_name_sca_key;

-- Add indexes similar to previously removed constraints as they are still needed for querying.
CREATE INDEX scan_results_with_artifact_provenance
    ON scan_results (artifact_url, artifact_hash, scanner_name, scanner_version, scanner_configuration);
CREATE INDEX scan_results_with_repository_provenance
    ON scan_results (vcs_type, vcs_url, vcs_revision, scanner_name, scanner_version, scanner_configuration);

-- Add table to associate scanner runs with scan results.
CREATE TABLE scanner_runs_scan_results
(
    scanner_run_id bigint REFERENCES scanner_runs NOT NULL,
    scan_result_id bigint REFERENCES scan_results NOT NULL,

    PRIMARY KEY (scanner_run_id, scan_result_id)
);
