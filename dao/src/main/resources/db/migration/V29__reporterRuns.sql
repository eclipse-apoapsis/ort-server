CREATE TABLE reporter_runs
(
    id                 bigserial PRIMARY KEY,
    reporter_job_id    bigint REFERENCES reporter_jobs NOT NULL,
    start_time         timestamp                       NOT NULL,
    end_time           timestamp                       NOT NULL
);

CREATE TABLE reports
(
    id                  bigserial PRIMARY KEY,
    report_filename     text                            NOT NULL
);

CREATE TABLE reporter_runs_reports
(
    reporter_run_id  bigint REFERENCES reporter_runs  NOT NULL,
    report_id        bigint REFERENCES reports        NOT NULL,

    PRIMARY KEY (reporter_run_id, report_id)
);
