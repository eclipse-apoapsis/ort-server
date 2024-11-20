-- This migration script deduplicates the scan_results table. Duplicates were possible in the past since all FossID
-- results have been stored without checking whether there is already a result with identical properties. Also, due to
-- a race condition when multiple ORT runs process the same packages concurrently, duplicates for other scanners could
-- have been created. After the deduplication, it is possible to again reintroduce unique constraints on the
-- scan_results table, which had been removed in migration 47.

-- Temporary function for assigning hash values to scan results based on their properties.
CREATE FUNCTION scan_result_hash(bigint) RETURNS text AS $$
DECLARE
  hash text;
BEGIN
    SELECT
      encode(sha256(convert_to(concat(
        sr.artifact_url,
        sr.artifact_hash,
        sr.artifact_hash_algorithm,
        sr.vcs_type,
        sr.vcs_url,
        sr.vcs_revision,
        sr.scanner_name,
        sr.scanner_version,
        sr.scanner_configuration,
        jsonb_hash_extended( sr.additional_data, 0)
      ), 'UTF8')),
        'hex')
    FROM scan_results sr
    WHERE sr.id = $1
    INTO hash;

    RETURN hash;
EXCEPTION WHEN others THEN
    RETURN NULL;
END;
$$
STRICT
LANGUAGE plpgsql IMMUTABLE;

-- A temporary view that assigns hash values to all scan_results.
CREATE VIEW scan_results_with_hashes as
SELECT
  sr.id,
  scan_result_hash(sr.id) hash
FROM scan_results sr;

-- A temporary table to hold the IDs of the remaining deduplicated scan_results.
CREATE TABLE scan_results_dedup
(
    id bigserial PRIMARY KEY
);

INSERT INTO scan_results_dedup
SELECT
  MIN(sr.id)
FROM scan_results_with_hashes sr
GROUP BY sr.hash;

-- Create link tables that use hashes to reference issues.

CREATE TABLE scanner_runs_scan_results_hashes
(
    scanner_run_id   bigint NOT NULL,
    scan_result_hash text   NOT NULL
);

INSERT INTO scanner_runs_scan_results_hashes
SELECT
  sr.scanner_run_id,
  srh.hash
FROM scanner_runs_scan_results sr
INNER JOIN scan_results_with_hashes srh on sr.scan_result_id = srh.id;

-- Delete duplicates.

DELETE FROM scanner_runs_scan_results;

-- Create a temporary foreign key index. This is needed, otherwise, the following DELETE statement would take
-- very long.
CREATE INDEX scanner_runs_scan_results_fkey
    ON scanner_runs_scan_results (scan_result_id);

DELETE FROM scan_results
WHERE id NOT IN (
  SELECT sd.id
  FROM scan_results_dedup sd
);

DROP INDEX scanner_runs_scan_results_fkey;

-- Recreate link tables.

INSERT INTO scanner_runs_scan_results
SELECT DISTINCT
  srs.scanner_run_id,
  srh.id
FROM scanner_runs_scan_results_hashes srs
INNER JOIN scan_results_with_hashes srh on srs.scan_result_hash = srh.hash;

-- Cleanup.

DROP VIEW scan_results_with_hashes;

DROP TABLE scan_results_dedup;

DROP TABLE scanner_runs_scan_results_hashes;

DROP FUNCTION scan_result_hash;

-- Reintroduce unique indexes.

CREATE UNIQUE INDEX on scan_results(
    artifact_url,
    artifact_hash,
    artifact_hash_algorithm,
    scanner_name,
    scanner_version,
    scanner_configuration,
    jsonb_hash_extended(additional_data, 0)
);

CREATE UNIQUE INDEX on scan_results(
    vcs_type,
    vcs_url,
    vcs_revision,
    scanner_name,
    scanner_version,
    scanner_configuration,
    jsonb_hash_extended(additional_data, 0)
);
