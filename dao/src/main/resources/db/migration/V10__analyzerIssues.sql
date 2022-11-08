CREATE TABLE ort_issues
(
    id        BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP    NOT NULL,
    source    VARCHAR(64)  NOT NULL,
    message   VARCHAR(256) NOT NULL,
    severity  VARCHAR(64)  NOT NULL
);

CREATE TABLE identifiers_ort_issues
(
    id            BIGSERIAL PRIMARY KEY,
    identifier_id BIGINT NOT NULL,
    ort_issue_id  BIGINT NOT NULL,

    CONSTRAINT fk_identifier
        FOREIGN KEY (identifier_id)
            REFERENCES identifiers (id),
    CONSTRAINT fk_ort_issue
        FOREIGN KEY (ort_issue_id)
            REFERENCES ort_issues (id)
);

CREATE TABLE analyzer_runs_identifiers_ort_issues
(
    analyzer_run_id         BIGINT NOT NULL,
    identifier_ort_issue_id BIGINT NOT NULL,

    CONSTRAINT pk_analyzer_run_identifier_ort_issue
        PRIMARY KEY (analyzer_run_id, identifier_ort_issue_id),
    CONSTRAINT fk_analyzer_run
        FOREIGN KEY (analyzer_run_id)
            REFERENCES analyzer_runs (id),
    CONSTRAINT fk_identifier_ort_issue
        FOREIGN KEY (identifier_ort_issue_id)
            REFERENCES identifiers_ort_issues (id)
);
