CREATE TABLE license_strings
(
    id   BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE license_spdx
(
    id         BIGSERIAL PRIMARY KEY,
    expression TEXT NOT NULL
);

CREATE TABLE identifiers
(
    id        BIGSERIAL PRIMARY KEY,
    type      TEXT NOT NULL,
    namespace TEXT NOT NULL,
    name      TEXT NOT NULL,
    version   TEXT NOT NULL
);

CREATE TABLE vcs_info
(
    id       BIGSERIAL PRIMARY KEY,
    type     TEXT NOT NULL,
    url      TEXT NOT NULL,
    revision TEXT NOT NULL,
    path     TEXT NOT NULL
);

CREATE TABLE authors
(
    id   BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE processed_declared_licenses
(
    id              BIGSERIAL PRIMARY KEY,
    license_spdx_id BIGINT REFERENCES license_spdx NOT NULL
);

CREATE TABLE projects
(
    id                            BIGSERIAL PRIMARY KEY,
    identifier_id                 BIGINT REFERENCES identifiers   NOT NULL,
    vcs_id                        BIGINT REFERENCES vcs_info      NOT NULL,
    vcs_processed_id              BIGINT REFERENCES vcs_info      NOT NULL,
    analyzer_run_id               BIGINT REFERENCES analyzer_runs NOT NULL,
    processed_declared_license_id BIGINT REFERENCES processed_declared_licenses,
    homepage_url                  TEXT                            NOT NULL,
    definition_file_path          TEXT                            NOT NULL,
    cpe                           TEXT                            NULL
);

CREATE TABLE projects_authors
(
    author_id  BIGINT REFERENCES authors  NOT NULL,
    project_id BIGINT REFERENCES projects NOT NULL,

    CONSTRAINT pk_projects_authors PRIMARY KEY (author_id, project_id)
);

CREATE TABLE projects_declared_licenses
(
    project_id        BIGINT REFERENCES projects        NOT NULL,
    license_string_id BIGINT REFERENCES license_strings NOT NULL,

    CONSTRAINT pk_projects_license_strings PRIMARY KEY (project_id, license_string_id)
);

CREATE TABLE processed_declared_licenses_unmapped_licenses
(
    processed_declared_license_id BIGINT REFERENCES processed_declared_licenses NOT NULL,
    license_string_id             BIGINT REFERENCES license_strings             NOT NULL,

    CONSTRAINT pk_processed_declared_licenses_unmapped_licenses
        PRIMARY KEY (processed_declared_license_id, license_string_id)
);

CREATE TABLE processed_declared_licenses_mapped_licenses
(
    id                            BIGSERIAL PRIMARY KEY,
    processed_declared_license_id BIGINT REFERENCES processed_declared_licenses NOT NULL,
    license_string_id             BIGINT REFERENCES license_strings             NOT NULL,
    license_spdx_id               BIGINT REFERENCES license_spdx                NOT NULL
);

CREATE TABLE project_scopes
(
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT REFERENCES projects NOT NULL,
    name       TEXT                       NOT NULL
);
