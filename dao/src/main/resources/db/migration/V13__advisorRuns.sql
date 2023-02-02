CREATE TABLE advisor_runs
(
    id             bigserial PRIMARY KEY,
    advisor_job_id bigint REFERENCES advisor_jobs NOT NULL,
    environment_id bigint REFERENCES environments NOT NULL,
    start_time     timestamp                      NOT NULL,
    end_time       timestamp                      NOT NULL
);

CREATE TABLE github_defects_configurations
(
    id                                  bigserial PRIMARY KEY,
    endpoint_url                        text NULL,
    label_filter                        text NULL,
    max_number_of_issues_per_repository int  NULL,
    parallel_requests                   int  NULL,

    UNIQUE (endpoint_url, label_filter, max_number_of_issues_per_repository, parallel_requests)
);

CREATE TABLE nexus_iq_configurations
(
    id         bigserial PRIMARY KEY,
    server_url text NOT NULL,
    browse_url text NOT NULL,

    UNIQUE (server_url, browse_url)
);

CREATE TABLE osv_configurations
(
    id         bigserial PRIMARY KEY,
    server_url text NULL,

    UNIQUE (server_url)
);

CREATE TABLE vulnerable_code_configurations
(
    id         bigserial PRIMARY KEY,
    server_url text NULL,

    UNIQUE (server_url)
);

CREATE TABLE advisor_configurations
(
    id                               bigserial PRIMARY KEY,
    advisor_run_id                   bigint REFERENCES advisor_runs ON DELETE CASCADE NOT NULL,
    github_defects_configuration_id  bigint REFERENCES github_defects_configurations  NULL,
    nexus_iq_configuration_id        bigint REFERENCES nexus_iq_configurations        NULL,
    osv_configuration_id             bigint REFERENCES osv_configurations             NULL,
    vulnerable_code_configuration_id bigint REFERENCES vulnerable_code_configurations NULL
);

CREATE TABLE advisor_configuration_options
(
    id                       bigserial PRIMARY KEY,
    advisor_configuration_id bigint REFERENCES advisor_configurations NOT NULL,
    key                      text                                     NOT NULL,
    value                    text                                     NOT NULL
);

CREATE TABLE defects
(
    id                  bigserial PRIMARY KEY,
    external_id         text      NOT NULL,
    url                 text      NOT NULL,
    title               text      NULL,
    state               text      NULL,
    severity            text      NULL,
    description         text      NULL,
    creation_time       timestamp NULL,
    modification_time   timestamp NULL,
    closing_time        timestamp NULL,
    fix_release_version text      NULL,
    fix_release_url     text      NULL
);

CREATE TABLE defect_labels
(
    id        bigserial PRIMARY KEY,
    defect_id bigint REFERENCES defects NOT NULL,
    key       text                      NOT NULL,
    value     text                      NOT NULL
);

CREATE TABLE vulnerabilities
(
    id          bigserial PRIMARY KEY,
    external_id text NOT NULL,
    summary     text NULL,
    description text NULL
);

CREATE TABLE vulnerability_references
(
    id               bigserial PRIMARY KEY,
    vulnerability_id bigint REFERENCES vulnerabilities NOT NULL,
    url              text                              NOT NULL,
    scoring_system   text                              NULL,
    severity         text                              NULL
);

CREATE TABLE advisor_runs_identifiers
(
    id             bigserial PRIMARY KEY,
    advisor_run_id bigint REFERENCES advisor_runs NOT NULL,
    identifier_id  bigint REFERENCES identifiers  NOT NULL
);

CREATE TABLE advisor_results
(
    id                        bigserial PRIMARY KEY,
    advisor_run_identifier_id bigint REFERENCES advisor_runs_identifiers NOT NULL,
    advisor_name              text                                       NOT NULL,
    capabilities              text                                       NOT NULL,
    start_time                timestamp                                  NOT NULL,
    end_time                  timestamp                                  NOT NULL
);

CREATE TABLE advisor_results_issues
(
    advisor_result_id bigint REFERENCES advisor_results NOT NULL,
    ort_issue_id      bigint REFERENCES ort_issues      NOT NULL,

    PRIMARY KEY (advisor_result_id, ort_issue_id)
);

CREATE TABLE advisor_results_defects
(
    advisor_result_id bigint REFERENCES advisor_results NOT NULL,
    defect_id         bigint REFERENCES defects         NOT NULL,

    PRIMARY KEY (advisor_result_id, defect_id)
);

CREATE TABLE advisor_results_vulnerabilities
(
    advisor_result_id bigint REFERENCES advisor_results NOT NULL,
    vulnerability_id  bigint REFERENCES vulnerabilities NOT NULL,

    PRIMARY KEY (advisor_result_id, vulnerability_id)
);
