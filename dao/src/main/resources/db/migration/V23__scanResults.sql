CREATE TABLE scan_summaries
(
    id                             bigserial PRIMARY KEY,
    start_time                     timestamp                            NOT NULL,
    end_time                       timestamp                            NOT NULL,
    package_verification_code      text                                 NOT NULL
);

CREATE TABLE license_findings
(
    id                             bigserial PRIMARY KEY,
    license                        text                                 NOT NULL,
    path                           text                                 NOT NULL,
    start_line                     int                                  NOT NULL,
    end_line                       int                                  NOT NULL,
    score                          real                                 NOT NULL,
    scan_summary_id                bigint REFERENCES scan_summaries     NOT NULL
);

CREATE TABLE copyright_findings
(
    id                             bigserial PRIMARY KEY,
    statement                      text                                 NOT NULL,
    path                           text                                 NOT NULL,
    start_line                     int                                  NOT NULL,
    end_line                       int                                  NOT NULL,
    scan_summary_id                bigint REFERENCES scan_summaries     NOT NULL
);

CREATE TABLE scan_summaries_issues
(
    scan_summary_id   bigint REFERENCES scan_summaries  NOT NULL,
    ort_issue_id      bigint REFERENCES ort_issues      NOT NULL,

    PRIMARY KEY (scan_summary_id, ort_issue_id)
);

CREATE TABLE scan_results
(
    id                       bigserial PRIMARY KEY,
    scan_summary_id          bigint REFERENCES scan_summaries               NOT NULL,
    artifact_url             text                                           NULL,
    artifact_hash            text                                           NULL,
    vcs_type                 text                                           NULL,
    vcs_url                  text                                           NULL,
    vcs_revision             text                                           NULL,
    scanner_name             text                                           NOT NULL,
    scanner_version          text                                           NOT NULL,
    scanner_configuration    text                                           NOT NULL,
    additional_data          jsonb                                          NULL,

    UNIQUE (artifact_url, artifact_hash, scanner_name, scanner_version, scanner_configuration),
    UNIQUE (vcs_type, vcs_url, vcs_revision, scanner_name, scanner_version, scanner_configuration)
);
