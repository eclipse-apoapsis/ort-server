CREATE TABLE ort_runs_issues
(
    ort_run_id      bigint REFERENCES ort_runs       NOT NULL,
    ort_issue_id    bigint REFERENCES ort_issues     NOT NULL,

    PRIMARY KEY (ort_run_id, ort_issue_id)
);
