CREATE TABLE ort_runs
(
    id                 bigserial PRIMARY KEY,
    index              integer                        NOT NULL,
    repository_id      bigint REFERENCES repositories NOT NULL,
    revision           text                           NOT NULL,
    created_at         timestamp                      NOT NULL,
    job_configurations jsonb                          NOT NULL,
    status             text                           NOT NULL,

    UNIQUE (index, repository_id)
);
