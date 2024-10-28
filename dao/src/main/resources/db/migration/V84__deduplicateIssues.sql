-- This migration script performs a deduplication of the entities in the issues table. It works analogously to
-- the deduplication script for packages and projects (V73__deduplicatePackagesAndProjects.sql) by assigning
-- hash values to the issues based on their properties that are then used to match references to the remaining
-- deduplicated issues.

-- Create missing foreign key indexes to the issues table. This is necessary to speed up the deduplication process.
CREATE INDEX ort_runs_issues_fkey on ort_runs_issues(issue_id);

CREATE INDEX scan_summaries_issues_fkey on scan_summaries_issues(issue_id);

-- Temporary function for assigning hash values to issues based on their properties.
CREATE FUNCTION issue_hash(bigint) RETURNS text AS $$
DECLARE
  hash text;
BEGIN
    SELECT
      encode(sha256(convert_to(concat(
        i."source",
        i.message,
        i.severity,
        i.affected_path
      ), 'UTF8')),
        'hex')
    FROM issues i
    WHERE i.id = $1
    INTO hash;

    RETURN hash;
EXCEPTION WHEN others THEN
    RETURN NULL;
END;
$$
STRICT
LANGUAGE plpgsql IMMUTABLE;

-- A temporary view that assigns hash values to all issues.
CREATE VIEW issues_with_hashes as
SELECT
  i.id,
  issue_hash(i.id) hash
FROM issues i;

-- A temporary table to hold the IDs of the remaining deduplicated issues.
CREATE TABLE issues_dedup
(
    id bigserial PRIMARY KEY
);

INSERT INTO issues_dedup
SELECT
  MIN(i.id)
FROM issues_with_hashes i
GROUP BY i.hash;

-- Create link tables that use hashes to reference issues.

CREATE TABLE ort_runs_issues_hashes
(
    id              bigserial                       PRIMARY KEY,
    ort_run_id      bigint REFERENCES ort_runs      NOT NULL,
    issue_hash      text                            NOT NULL,
    identifier_id   bigint REFERENCES identifiers       NULL,
    worker          text                                NULL,
    timestamp       timestamp                       NOT NULL
);

INSERT INTO ort_runs_issues_hashes
SELECT
  ori.id, ori.ort_run_id, ih.hash, ori.identifier_id, ori.worker, ori."timestamp"
FROM ort_runs_issues ori
INNER JOIN issues_with_hashes ih on ori.issue_id = ih.id;

CREATE TABLE scan_summaries_issues_hashes
(
    id                bigserial                         PRIMARY KEY,
    scan_summary_id   bigint REFERENCES scan_summaries  NOT NULL,
    issue_hash        text                              NOT NULL,
    timestamp         timestamp                         NOT NULL
);

INSERT INTO scan_summaries_issues_hashes
SELECT
  ssi.id, ssi.scan_summary_id, ih.hash, ssi."timestamp"
FROM scan_summaries_issues ssi
INNER JOIN issues_with_hashes ih on ssi.issue_id = ih.id;

CREATE TABLE identifiers_issues_hashes
(
    identifier_id   bigint REFERENCES identifiers   NOT NULL,
    issue_hash      text                            NOT NULL
);

INSERT INTO identifiers_issues_hashes
SELECT
  ii.identifier_id, ih.hash
FROM identifiers_issues ii
INNER JOIN issues_with_hashes ih on ii.issue_id = ih.id;

-- Remove duplicates

DELETE FROM ort_runs_issues;

DELETE FROM scan_summaries_issues;

DELETE FROM identifiers_issues;

DELETE FROM issues i
WHERE NOT EXISTS (
  SELECT FROM issues_dedup id
  WHERE id.id = i.id
);

-- Recreate link tables.

INSERT INTO ort_runs_issues
SELECT
  ori.id,
  ori.ort_run_id,
  ih.id,
  ori.identifier_id,
  ori.worker,
  ori."timestamp"
FROM
  ort_runs_issues_hashes ori
INNER JOIN issues_with_hashes ih ON ori.issue_hash = ih.hash;

INSERT INTO scan_summaries_issues
SELECT
  ssi.id,
  ssi.scan_summary_id,
  ih.id,
  ssi."timestamp"
FROM
  scan_summaries_issues_hashes ssi
INNER JOIN issues_with_hashes ih ON ssi.issue_hash = ih.hash;

INSERT INTO identifiers_issues
("identifier_id", "issue_id")
SELECT
  iih.identifier_id,
  ih.id
FROM
  identifiers_issues_hashes iih
INNER JOIN issues_with_hashes ih ON iih.issue_hash = ih.hash;

-- Cleanup

DROP TABLE issues_dedup;

DROP TABLE ort_runs_issues_hashes;

DROP TABLE scan_summaries_issues_hashes;

DROP VIEW issues_with_hashes;

DROP FUNCTION issue_hash;
