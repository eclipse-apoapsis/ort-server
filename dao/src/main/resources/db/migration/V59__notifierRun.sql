CREATE TABLE notifier_runs
(
    id                 bigserial PRIMARY KEY,
    notifier_job_id    bigint REFERENCES notifier_jobs NOT NULL,
    start_time         timestamp                       NOT NULL,
    end_time           timestamp                       NOT NULL
);
