CREATE TABLE ort_runs
(
    id                 BIGSERIAL PRIMARY KEY,
    index              INTEGER   NOT NULL,
    fk_repository      BIGSERIAL NOT NULL,
    revision           TEXT      NOT NULL,
    created_at         TIMESTAMP NOT NULL,
    job_configurations JSONB     NOT NULL,
    status             TEXT      NOT NULL,
    CONSTRAINT fk_ort_runs_repositories
        FOREIGN KEY (fk_repository)
            REFERENCES repositories (id),
    CONSTRAINT unique_index
        UNIQUE (index, fk_repository)
)
