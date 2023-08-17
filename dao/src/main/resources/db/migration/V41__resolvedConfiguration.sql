CREATE TABLE resolved_configurations
(
    id         bigserial PRIMARY KEY,
    ort_run_id bigint REFERENCES ort_runs NOT NULL
);

CREATE TABLE resolved_configurations_package_configurations
(
    resolved_configuration_id bigint REFERENCES resolved_configurations NOT NULL,
    package_configuration_id  bigint REFERENCES package_configurations  NOT NULL,

    PRIMARY KEY (resolved_configuration_id, package_configuration_id)
);

CREATE TABLE resolved_configurations_issue_resolutions
(
    resolved_configuration_id bigint REFERENCES resolved_configurations NOT NULL,
    issue_resolution_id       bigint REFERENCES issue_resolutions       NOT NULL,

    PRIMARY KEY (resolved_configuration_id, issue_resolution_id)
);

CREATE TABLE resolved_configurations_rule_violation_resolutions
(
    resolved_configuration_id    bigint REFERENCES resolved_configurations    NOT NULL,
    rule_violation_resolution_id bigint REFERENCES rule_violation_resolutions NOT NULL,

    PRIMARY KEY (resolved_configuration_id, rule_violation_resolution_id)
);

CREATE TABLE resolved_configurations_vulnerability_resolutions
(
    resolved_configuration_id   bigint REFERENCES resolved_configurations   NOT NULL,
    vulnerability_resolution_id bigint REFERENCES vulnerability_resolutions NOT NULL,

    PRIMARY KEY (resolved_configuration_id, vulnerability_resolution_id)
);

CREATE TABLE package_curation_provider_configs
(
    id   bigserial PRIMARY KEY,
    name text NOT NULL,

    UNIQUE (name)
);

CREATE TABLE resolved_package_curation_providers
(
    id                                  bigserial PRIMARY KEY,
    resolved_configuration_id           bigint REFERENCES resolved_configurations           NOT NULL,
    package_curation_provider_config_id bigint REFERENCES package_curation_provider_configs NOT NULL,
    rank                                int                                                 NOT NULL,

    UNIQUE (resolved_configuration_id, package_curation_provider_config_id),
    UNIQUE (resolved_configuration_id, rank)
);

CREATE TABLE resolved_package_curations
(
    id                                    bigserial PRIMARY KEY,
    resolved_package_curation_provider_id bigint REFERENCES resolved_package_curation_providers NOT NULL,
    package_curation_id                   bigint REFERENCES package_curations                   NOT NULL,
    rank                                  int                                                   NOT NULL,

    UNIQUE (resolved_package_curation_provider_id, package_curation_id),
    UNIQUE (resolved_package_curation_provider_id, rank)
);
