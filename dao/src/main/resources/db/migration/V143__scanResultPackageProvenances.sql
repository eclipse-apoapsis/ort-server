-- Add a table to join scan results to package provenances.
-- ON DELETE CASCADE on both FKs ensure table rows are deleted
-- when orphan-removal code is run on the server.
CREATE TABLE scan_result_package_provenances
(
    scan_result_id        bigint REFERENCES scan_results        ON DELETE CASCADE NOT NULL,
    package_provenance_id bigint REFERENCES package_provenances ON DELETE CASCADE NOT NULL,

    PRIMARY KEY (scan_result_id, package_provenance_id)
);
