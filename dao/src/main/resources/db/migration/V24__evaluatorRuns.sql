CREATE TABLE evaluator_runs
(
    id                             bigserial PRIMARY KEY,
    start_time                     timestamp                            NOT NULL,
    end_time                       timestamp                            NOT NULL,
    evaluator_job_id               bigint REFERENCES evaluator_jobs     NOT NULL
);

CREATE TABLE rule_violations
(
    id                             bigserial PRIMARY KEY,
    rule                           text                                 NOT NULL,
    package_identifier_id          bigint REFERENCES identifiers        NULL,
    license                        text                                 NULL,
    license_source                 text                                 NULL,
    severity                       text                                 NOT NULL,
    message                        text                                 NOT NULL,
    how_to_fix                     text                                 NOT NULL,

    UNIQUE (rule, package_identifier_id, license, license_source, severity, message, how_to_fix)
);

CREATE TABLE evaluator_runs_rule_violations
(
    evaluator_run_id         bigint REFERENCES evaluator_runs          NOT NULL,
    rule_violation_id        bigint REFERENCES rule_violations         NOT NULL,

    PRIMARY KEY (evaluator_run_id, rule_violation_id)
);
