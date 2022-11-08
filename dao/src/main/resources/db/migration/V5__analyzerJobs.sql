CREATE TABLE analyzer_jobs
(
    id            BIGSERIAL PRIMARY KEY,
    ort_run_id    BIGINT REFERENCES ort_runs NOT NULL,
    created_at    TIMESTAMP                  NOT NULL,
    started_at    TIMESTAMP                  NULL,
    finished_at   TIMESTAMP                  NULL,
    configuration JSONB                      NOT NULL,
    status        TEXT                       NOT NULL
);
