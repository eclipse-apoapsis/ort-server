-- This migration script extends the ort_runs_issues table with the goal to make it the central table that
-- associates issues with ORT runs; it is going to replace all other link tables specific to workers.
-- This is a multi-step approach, however. In this migration, only the foundations are laid, and the issues
-- assigned to the ORT runs themselves are handled.

-- Create a temporary table with the desired target structure. This is necessary because new non-nullable columns
-- without default values are to be added. Therefore, this new table is created, then data is added, and finally
-- the table replaces the original one.
CREATE TABLE ort_runs_issues2
(
    id              bigserial                       PRIMARY KEY,
    ort_run_id      bigint REFERENCES ort_runs      NOT NULL,
    issue_id        bigint REFERENCES issues        NOT NULL,
    identifier_id   bigint REFERENCES identifiers       NULL,
    worker          text                                NULL,
    timestamp       timestamp                       NOT NULL
);

INSERT INTO ort_runs_issues2
("ort_run_id", "issue_id", "identifier_id", "worker", "timestamp")
SELECT
  ori.ort_run_id,
  ori.issue_id,
  NULL,
  CASE WHEN LOWER(i.source) = 'analyzer' THEN 'analyzer'
       WHEN LOWER(i.source) = 'advisor' THEN 'advisor'
       WHEN LOWER(i.source) = 'scanner' THEN 'scanner'
       WHEN LOWER(i.source) = 'evaluator' THEN 'evaluator'
       WHEN LOWER(i.source) = 'reporter' THEN 'reporter'
       WHEN LOWER(i.source) = 'notifier' THEN 'notifier'
       ELSE 'config'
  END,
  i.timestamp
FROM
  ort_runs_issues ori
  INNER JOIN issues i ON ori.issue_id = i.id;

DROP TABLE ort_runs_issues;

ALTER TABLE ort_runs_issues2 RENAME TO ort_runs_issues;
