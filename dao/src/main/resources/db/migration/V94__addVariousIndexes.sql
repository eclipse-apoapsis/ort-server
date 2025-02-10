-- The existing primary key index uses the columns in the opposite order and cannot be used when joining by analyzer_run_id.
CREATE INDEX IF NOT EXISTS packages_analyzer_runs_analyzer_run_id_package_id ON packages_analyzer_runs (analyzer_run_id, package_id);

-- These indexes speed up joining the tables by job id.
CREATE INDEX IF NOT EXISTS advisor_runs_advisor_job_id ON advisor_runs (advisor_job_id);
CREATE INDEX IF NOT EXISTS analyzer_runs_analyzer_job_id ON analyzer_runs (analyzer_job_id);
CREATE INDEX IF NOT EXISTS evaluator_runs_evaluator_job_id ON evaluator_runs (evaluator_job_id);
CREATE INDEX IF NOT EXISTS notifier_runs_notifier_job_id ON notifier_runs (notifier_job_id);
CREATE INDEX IF NOT EXISTS reporter_runs_reporter_job_id ON reporter_runs (reporter_job_id);
CREATE INDEX IF NOT EXISTS scanner_runs_scanner_job_id ON scanner_runs (scanner_job_id);

-- These indexes speed up getting the vulnerabilities for a run.
CREATE INDEX IF NOT EXISTS advisor_results_advisor_run_identifier_id ON advisor_results (advisor_run_identifier_id);
CREATE INDEX IF NOT EXISTS advisor_runs_identifiers_advisor_run_id ON advisor_runs_identifiers (advisor_run_id);

-- This index speeds up getting the issues for a run.
CREATE INDEX IF NOT EXISTS ort_runs_issues_ort_run_id ON ort_runs_issues (ort_run_id);
