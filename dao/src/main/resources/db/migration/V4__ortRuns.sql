CREATE TABLE ort_runs
(
    id                 BIGSERIAL PRIMARY KEY,
    index              INTEGER                        NOT NULL,
    repository_id      BIGINT REFERENCES repositories NOT NULL,
    revision           TEXT                           NOT NULL,
    created_at         TIMESTAMP                      NOT NULL,
    job_configurations JSONB                          NOT NULL,
    status             TEXT                           NOT NULL,
    CONSTRAINT unique_index
        UNIQUE (index, repository_id)
)
