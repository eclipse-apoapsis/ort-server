ALTER TABLE analyzer_jobs
ADD CONSTRAINT analyzer_jobs_ort_run_id_key UNIQUE (ort_run_id);

ALTER TABLE advisor_jobs
ADD CONSTRAINT advisor_jobs_ort_run_id_key UNIQUE (ort_run_id);

ALTER TABLE scanner_jobs
ADD CONSTRAINT scanner_jobs_ort_run_id_key UNIQUE (ort_run_id);

ALTER TABLE reporter_jobs
ADD CONSTRAINT reporter_jobs_ort_run_id_key UNIQUE (ort_run_id);

ALTER TABLE evaluator_jobs
ADD CONSTRAINT evaluator_jobs_ort_run_id_key UNIQUE (ort_run_id);
