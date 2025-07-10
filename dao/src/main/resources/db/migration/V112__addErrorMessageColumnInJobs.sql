-- Adds new column `error_message` to every *_jobs table
ALTER TABLE advisor_jobs
    ADD COLUMN error_message text NULL;

ALTER TABLE analyzer_jobs
    ADD COLUMN error_message text NULL;

ALTER TABLE evaluator_jobs
    ADD COLUMN error_message text NULL;

ALTER TABLE notifier_jobs
    ADD COLUMN error_message text NULL;

ALTER TABLE reporter_jobs
    ADD COLUMN error_message text NULL;

ALTER TABLE scanner_jobs
    ADD COLUMN error_message text NULL;
