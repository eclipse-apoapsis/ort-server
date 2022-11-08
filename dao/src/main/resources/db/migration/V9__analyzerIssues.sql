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
    identifier_id BIGINT REFERENCES identifiers NOT NULL,
    ort_issue_id  BIGINT REFERENCES ort_issues  NOT NULL
);

CREATE TABLE analyzer_runs_identifiers_ort_issues
(
    analyzer_run_id         BIGINT REFERENCES analyzer_runs          NOT NULL,
    identifier_ort_issue_id BIGINT REFERENCES identifiers_ort_issues NOT NULL,

    CONSTRAINT pk_analyzer_run_identifier_ort_issue
        PRIMARY KEY (analyzer_run_id, identifier_ort_issue_id)
);
