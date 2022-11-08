CREATE TABLE analyzer_jobs
(
    id            BIGSERIAL PRIMARY KEY,
    fk_ort_run    BIGSERIAL NOT NULL UNIQUE,
    created_at    TIMESTAMP NOT NULL,
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    configuration JSONB     NOT NULL,
    status        TEXT      NOT NULL
)
