-- Drop unique constraints as multiple entries are now allowed.
ALTER TABLE package_provenances
    DROP CONSTRAINT package_provenances_identifier_id_artifact_id_key,
    DROP CONSTRAINT package_provenances_identifier_id_vcs_id_key;

-- Add indexes similar to previously removed constraints as they are still needed for querying.
CREATE INDEX package_provenances_identifier_id_artifact_id ON package_provenances (identifier_id, artifact_id);
CREATE INDEX package_provenances_identifier_id_vcs_id ON package_provenances (identifier_id, vcs_id);

-- Add table to associate scanner runs with package provenances.
CREATE TABLE scanner_runs_package_provenances
(
    scanner_run_id        bigint REFERENCES scanner_runs        NOT NULL,
    package_provenance_id bigint REFERENCES package_provenances NOT NULL,

    PRIMARY KEY (scanner_run_id, package_provenance_id)
);
