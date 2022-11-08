CREATE TABLE license_strings
(
    id   bigserial PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE license_spdx
(
    id         bigserial PRIMARY KEY,
    expression text NOT NULL
);

CREATE TABLE identifiers
(
    id        bigserial PRIMARY KEY,
    type      text NOT NULL,
    namespace text NOT NULL,
    name      text NOT NULL,
    version   text NOT NULL
);

CREATE TABLE vcs_info
(
    id       bigserial PRIMARY KEY,
    type     text NOT NULL,
    url      text NOT NULL,
    revision text NOT NULL,
    path     text NOT NULL
);

CREATE TABLE authors
(
    id   bigserial PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE processed_declared_licenses
(
    id              bigserial PRIMARY KEY,
    license_spdx_id bigint REFERENCES license_spdx NOT NULL
);

CREATE TABLE projects
(
    id                            bigserial PRIMARY KEY,
    identifier_id                 bigint REFERENCES identifiers   NOT NULL,
    vcs_id                        bigint REFERENCES vcs_info      NOT NULL,
    vcs_processed_id              bigint REFERENCES vcs_info      NOT NULL,
    analyzer_run_id               bigint REFERENCES analyzer_runs NOT NULL,
    processed_declared_license_id bigint REFERENCES processed_declared_licenses,
    homepage_url                  text                            NOT NULL,
    definition_file_path          text                            NOT NULL,
    cpe                           text                            NULL
);

CREATE TABLE projects_authors
(
    author_id  bigint REFERENCES authors  NOT NULL,
    project_id bigint REFERENCES projects NOT NULL,

    PRIMARY KEY (author_id, project_id)
);

CREATE TABLE projects_declared_licenses
(
    project_id        bigint REFERENCES projects        NOT NULL,
    license_string_id bigint REFERENCES license_strings NOT NULL,

    PRIMARY KEY (project_id, license_string_id)
);

CREATE TABLE processed_declared_licenses_unmapped_licenses
(
    processed_declared_license_id bigint REFERENCES processed_declared_licenses NOT NULL,
    license_string_id             bigint REFERENCES license_strings             NOT NULL,

    PRIMARY KEY (processed_declared_license_id, license_string_id)
);

CREATE TABLE processed_declared_licenses_mapped_licenses
(
    id                            bigserial PRIMARY KEY,
    processed_declared_license_id bigint REFERENCES processed_declared_licenses NOT NULL,
    license_string_id             bigint REFERENCES license_strings             NOT NULL,
    license_spdx_id               bigint REFERENCES license_spdx                NOT NULL
);

CREATE TABLE project_scopes
(
    id         bigserial PRIMARY KEY,
    project_id bigint REFERENCES projects NOT NULL,
    name       text                       NOT NULL
);
