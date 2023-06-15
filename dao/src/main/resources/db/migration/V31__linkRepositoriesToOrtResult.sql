ALTER TABLE ort_runs
    ADD COLUMN vcs_id                  bigint REFERENCES vcs_info NULL,
    ADD COLUMN vcs_processed_id        bigint REFERENCES vcs_info NULL
;

CREATE TABLE nested_repositories
(
    id                      bigserial                       PRIMARY KEY,
    ort_run_id              bigint REFERENCES ort_runs      NOT NULL,
    vcs_id                  bigint REFERENCES vcs_info      NOT NULL,
    path                    text                            NOT NULL,

    UNIQUE (ort_run_id, vcs_id, path)
);
