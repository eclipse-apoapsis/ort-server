CREATE TABLE scanner_jobs
(
    id            bigserial PRIMARY KEY,
    ort_run_id    bigint REFERENCES ort_runs NOT NULL,
    created_at    timestamp                  NOT NULL,
    started_at    timestamp                  NULL,
    finished_at   timestamp                  NULL,
    configuration jsonb                      NOT NULL,
    status        text                       NOT NULL
);
