CREATE INDEX IF NOT EXISTS analyzer_jobs_durations ON analyzer_jobs (status, started_at, finished_at);
CREATE INDEX IF NOT EXISTS advisor_jobs_durations ON advisor_jobs (status, started_at, finished_at);
CREATE INDEX IF NOT EXISTS evaluator_jobs_durations ON evaluator_jobs (status, started_at, finished_at);
CREATE INDEX IF NOT EXISTS scanner_jobs_durations ON scanner_jobs (status, started_at, finished_at);
CREATE INDEX IF NOT EXISTS reporter_jobs_durations ON reporter_jobs (status, started_at, finished_at);
CREATE INDEX IF NOT EXISTS notifier_jobs_durations ON notifier_jobs (status, started_at, finished_at);
CREATE INDEX IF NOT EXISTS ort_runs_durations ON ort_runs (status, finished_at);