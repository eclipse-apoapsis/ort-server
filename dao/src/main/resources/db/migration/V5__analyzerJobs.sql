CREATE TABLE analyzer_jobs
(
    id            SERIAL PRIMARY KEY,
    fk_ort_run    SERIAL    NOT NULL UNIQUE,
    created_at    TIMESTAMP NOT NULL,
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    configuration JSONB     NOT NULL,
    status        TEXT      NOT NULL
)
