ALTER TABLE scan_results
    DROP CONSTRAINT scan_results_artifact_url_artifact_hash_scanner_name_scanne_key;

ALTER TABLE scan_results
    ADD COLUMN artifact_hash_algorithm text NULL;

ALTER TABLE scan_results
    ADD UNIQUE (artifact_url, artifact_hash, artifact_hash_algorithm, scanner_name, scanner_version,
                scanner_configuration);
