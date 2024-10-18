-- This migration script extracts the timestamp from the issues table and moves it up to the scan_summaries_issues
-- table. This is a preparation to deduplicate the entities in the issues table; the table will store only the
-- most relevant properties for issues.

-- Since the timestamp column is not nullable, create a new table that is populated and then renamed.
CREATE TABLE scan_summaries_issues_with_timestamps
(
    id                bigserial                         PRIMARY KEY,
    scan_summary_id   bigint REFERENCES scan_summaries  NOT NULL,
    issue_id          bigint REFERENCES issues          NOT NULL,
    timestamp         timestamp                         NOT NULL
);

INSERT INTO scan_summaries_issues_with_timestamps
("scan_summary_id", "issue_id", "timestamp")
SELECT
  ssi.scan_summary_id,
  ssi.issue_id,
  i.timestamp
FROM
  scan_summaries_issues ssi
  INNER JOIN issues i ON ssi.issue_id = i.id;

DROP TABLE scan_summaries_issues;

ALTER TABLE scan_summaries_issues_with_timestamps RENAME TO scan_summaries_issues;

-- Remove the timestamp column from the issues table, since it is no longer used.
ALTER TABLE issues DROP COLUMN timestamp;
