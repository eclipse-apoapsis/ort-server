ALTER TABLE ort_issues
    RENAME TO issues;

ALTER TABLE identifiers_ort_issues
    RENAME TO identifiers_issues;

ALTER TABLE identifiers_issues
    RENAME COLUMN ort_issue_id TO issue_id;

ALTER TABLE analyzer_runs_identifiers_ort_issues
    RENAME TO analyzer_runs_identifiers_issues;

ALTER TABLE analyzer_runs_identifiers_issues
    RENAME COLUMN identifier_ort_issue_id TO identifier_issue_id;

ALTER TABLE ort_runs_issues
    RENAME COLUMN ort_issue_id TO issue_id;

ALTER TABLE scan_summaries_issues
    RENAME COLUMN ort_issue_id TO issue_id;

ALTER TABLE advisor_results_issues
    RENAME COLUMN ort_issue_id TO issue_id;
