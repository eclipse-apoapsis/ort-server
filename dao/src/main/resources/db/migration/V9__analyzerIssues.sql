CREATE TABLE ort_issues
(
    id        bigserial PRIMARY KEY,
    timestamp timestamp    NOT NULL,
    source    varchar(64)  NOT NULL,
    message   varchar(256) NOT NULL,
    severity  varchar(64)  NOT NULL,

    UNIQUE (timestamp, source, message, severity)
);

CREATE TABLE identifiers_ort_issues
(
    id            bigserial PRIMARY KEY,
    identifier_id bigint REFERENCES identifiers NOT NULL,
    ort_issue_id  bigint REFERENCES ort_issues  NOT NULL,

    UNIQUE (identifier_id, ort_issue_id)
);

CREATE TABLE analyzer_runs_identifiers_ort_issues
(
    analyzer_run_id         bigint REFERENCES analyzer_runs          NOT NULL,
    identifier_ort_issue_id bigint REFERENCES identifiers_ort_issues NOT NULL,

    PRIMARY KEY (analyzer_run_id, identifier_ort_issue_id)
);
