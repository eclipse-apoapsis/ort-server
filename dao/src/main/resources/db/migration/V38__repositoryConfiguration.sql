CREATE TABLE repository_analyzer_configurations
(
    id                        bigserial PRIMARY KEY,
    allow_dynamic_versions    boolean,
    enabled_package_managers  text,
    disabled_package_managers text,
    skip_excluded             boolean
);

CREATE INDEX repository_analyzer_configurations_all_value_columns
    ON repository_analyzer_configurations (allow_dynamic_versions, enabled_package_managers, disabled_package_managers,
                                           skip_excluded);

CREATE TABLE repository_analyzer_configurations_package_manager_configurations
(
    repository_analyzer_configuration_id bigint REFERENCES repository_analyzer_configurations NOT NULL,
    package_manager_configuration_id     bigint REFERENCES package_manager_configurations     NOT NULL,

    PRIMARY KEY (repository_analyzer_configuration_id, package_manager_configuration_id)
);

CREATE TABLE repository_configurations
(
    id                                   bigserial PRIMARY KEY,
    ort_run_id                           bigint REFERENCES ort_runs NOT NULL,
    repository_analyzer_configuration_id bigint REFERENCES repository_analyzer_configurations
);

CREATE TABLE issue_resolutions
(
    id      bigserial PRIMARY KEY,
    message text NOT NULL,
    reason  text NOT NULL,
    comment text NOT NULL
);

CREATE INDEX issue_resolutions_all_value_columns
    ON issue_resolutions (message, reason, comment);

CREATE TABLE rule_violation_resolutions
(
    id      bigserial PRIMARY KEY,
    message text NOT NULL,
    reason  text NOT NULL,
    comment text NOT NULL
);

CREATE INDEX rule_violation_resolutions_all_value_columns
    ON rule_violation_resolutions (message, reason, comment);

CREATE TABLE vulnerability_resolutions
(
    id          bigserial PRIMARY KEY,
    external_id text NOT NULL,
    reason      text NOT NULL,
    comment     text NOT NULL
);

CREATE INDEX vulnerability_resolutions_all_value_columns
    ON vulnerability_resolutions (external_id, reason, comment);

CREATE TABLE repository_configurations_issue_resolutions
(
    repository_configuration_id bigint REFERENCES repository_configurations NOT NULL,
    issue_resolution_id         bigint REFERENCES issue_resolutions         NOT NULL,

    PRIMARY KEY (repository_configuration_id, issue_resolution_id)
);

CREATE TABLE repository_configurations_rule_violation_resolutions
(
    repository_configuration_id  bigint REFERENCES repository_configurations  NOT NULL,
    rule_violation_resolution_id bigint REFERENCES rule_violation_resolutions NOT NULL,

    PRIMARY KEY (repository_configuration_id, rule_violation_resolution_id)
);

CREATE TABLE repository_configurations_vulnerability_resolutions
(
    repository_configuration_id bigint REFERENCES repository_configurations NOT NULL,
    vulnerability_resolution_id bigint REFERENCES vulnerability_resolutions NOT NULL,

    PRIMARY KEY (repository_configuration_id, vulnerability_resolution_id)
);

CREATE TABLE scope_excludes
(
    id      bigserial PRIMARY KEY,
    pattern text NOT NULL,
    reason  text NOT NULL,
    comment text NOT NULL
);

CREATE INDEX scope_excludes_all_value_columns
    ON scope_excludes (pattern, reason, comment);

CREATE TABLE path_excludes
(
    id      bigserial PRIMARY KEY,
    pattern text NOT NULL,
    reason  text NOT NULL,
    comment text NOT NULL
);

CREATE INDEX path_excludes_all_value_columns
    ON path_excludes (pattern, reason, comment);

CREATE TABLE repository_configurations_scope_excludes
(
    repository_configuration_id bigint REFERENCES repository_configurations NOT NULL,
    scope_exclude_id            bigint REFERENCES scope_excludes            NOT NULL,

    PRIMARY KEY (repository_configuration_id, scope_exclude_id)
);

CREATE TABLE repository_configurations_path_excludes
(
    repository_configuration_id bigint REFERENCES repository_configurations NOT NULL,
    path_exclude_id             bigint REFERENCES path_excludes             NOT NULL,

    PRIMARY KEY (repository_configuration_id, path_exclude_id)
);

CREATE TABLE vcs_info_curation_data
(
    id       bigserial PRIMARY KEY,
    type     text,
    url      text,
    revision text,
    path     text
);

CREATE INDEX vcs_info_curation_data_all_value_columns
    ON vcs_info_curation_data (type, url, revision, path);

CREATE TABLE package_curation_data
(
    id                        bigserial PRIMARY KEY,
    binary_artifact_id        bigint REFERENCES remote_artifacts,
    source_artifact_id        bigint REFERENCES remote_artifacts,
    vcs_info_curation_data_id bigint REFERENCES vcs_info_curation_data,

    comment                   text,
    purl                      text,
    cpe                       text,
    concluded_license         text,
    description               text,
    homepage_url              text,
    is_metadata_only          boolean,
    is_modified               boolean
);

CREATE INDEX package_curation_data_all_value_columns
    ON package_curation_data (comment, purl, cpe, concluded_license, description, homepage_url, is_metadata_only,
                              is_modified);

CREATE TABLE declared_license_mappings
(
    id           bigserial PRIMARY KEY,
    license      text NOT NULL,
    spdx_license text NOT NULL
);

CREATE INDEX declared_license_mappings_all_value_columns
    ON declared_license_mappings (license, spdx_license);

CREATE TABLE package_curation_data_declared_license_mappings
(
    package_curation_data_id    bigint REFERENCES package_curation_data     NOT NULL,
    declared_license_mapping_id bigint REFERENCES declared_license_mappings NOT NULL,

    PRIMARY KEY (package_curation_data_id, declared_license_mapping_id)
);

CREATE TABLE package_curation_data_authors
(
    author_id                bigint REFERENCES authors               NOT NULL,
    package_curation_data_id bigint REFERENCES package_curation_data NOT NULL,

    PRIMARY KEY (author_id, package_curation_data_id)
);

CREATE TABLE package_curations
(
    id                       bigserial PRIMARY KEY,
    identifier_id            bigint REFERENCES identifiers           NOT NULL,
    package_curation_data_id bigint REFERENCES package_curation_data NOT NULL
);

CREATE TABLE license_finding_curations
(
    id                bigserial PRIMARY KEY,
    path              text NOT NULL,
    start_lines       text,
    line_count        integer,
    detected_license  text,
    concluded_license text NOT NULL,
    reason            text NOT NULL,
    comment           text NOT NULL
);

CREATE INDEX license_finding_curations_all_value_columns
    ON license_finding_curations (path, start_lines, line_count, detected_license, concluded_license, reason, comment);

CREATE TABLE repository_configurations_license_finding_curations
(
    repository_configuration_id bigint REFERENCES repository_configurations NOT NULL,
    license_finding_curation_id bigint REFERENCES license_finding_curations NOT NULL,

    PRIMARY KEY (repository_configuration_id, license_finding_curation_id)
);

CREATE TABLE repository_configurations_package_curations
(
    repository_configuration_id bigint REFERENCES repository_configurations NOT NULL,
    package_curation_id         bigint REFERENCES package_curations         NOT NULL,

    PRIMARY KEY (repository_configuration_id, package_curation_id)
);

CREATE TABLE vcs_matchers
(
    id       bigserial PRIMARY KEY,
    type     text NOT NULL,
    url      text NOT NULL,
    revision text
);

CREATE INDEX vcs_matchers_all_value_columns
    ON vcs_matchers (type, url, revision);

CREATE TABLE package_configurations
(
    id                  bigserial PRIMARY KEY,
    identifier_id       bigint REFERENCES identifiers NOT NULL,
    vcs_matcher_id      bigint REFERENCES vcs_matchers,
    source_artifact_url text
);

CREATE INDEX package_configurations_all_value_columns
    ON package_configurations (identifier_id, vcs_matcher_id, source_artifact_url);

CREATE TABLE package_configurations_path_excludes
(
    package_configuration_id bigint REFERENCES package_configurations NOT NULL,
    path_exclude_id          bigint REFERENCES path_excludes          NOT NULL,

    PRIMARY KEY (package_configuration_id, path_exclude_id)
);

CREATE TABLE package_configurations_license_finding_curations
(
    package_configuration_id    bigint REFERENCES package_configurations    NOT NULL,
    license_finding_curation_id bigint REFERENCES license_finding_curations NOT NULL,

    PRIMARY KEY (package_configuration_id, license_finding_curation_id)
);

CREATE TABLE repository_configurations_package_configurations
(
    repository_configuration_id bigint REFERENCES repository_configurations NOT NULL,
    package_configuration_id    bigint REFERENCES package_configurations    NOT NULL,

    PRIMARY KEY (repository_configuration_id, package_configuration_id)
);

CREATE TABLE spdx_license_choices
(
    id     bigserial PRIMARY KEY,
    given  text,
    choice text NOT NULL
);

CREATE INDEX spdx_license_choices_all_value_columns
    ON spdx_license_choices (given, choice);

CREATE TABLE repository_configurations_spdx_license_choices
(
    repository_configuration_id bigint REFERENCES repository_configurations NOT NULL,
    spdx_license_choice         bigint REFERENCES spdx_license_choices      NOT NULL,

    PRIMARY KEY (repository_configuration_id, spdx_license_choice)
);

CREATE TABLE package_license_choices
(
    id            bigserial PRIMARY KEY,
    identifier_id bigint REFERENCES identifiers NOT NULL
);

CREATE TABLE package_license_choices_spdx_license_choices
(
    package_license_choice_id bigint REFERENCES package_license_choices NOT NULL,
    spdx_license_choice_id    bigint REFERENCES spdx_license_choices    NOT NULL,

    PRIMARY KEY (package_license_choice_id, spdx_license_choice_id)
);

CREATE TABLE repository_configurations_package_license_choices
(
    repository_configuration_id bigint REFERENCES repository_configurations NOT NULL,
    package_license_choice_id   bigint REFERENCES package_license_choices   NOT NULL,

    PRIMARY KEY (repository_configuration_id, package_license_choice_id)
);
