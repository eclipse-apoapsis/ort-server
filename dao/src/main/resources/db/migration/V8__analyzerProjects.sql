CREATE TABLE license_strings
(
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE license_spdx
(
    id BIGSERIAL PRIMARY KEY,
    expression TEXT NOT NULL
);

CREATE TABLE identifiers
(
    id BIGSERIAL PRIMARY KEY,
    type TEXT NOT NULL,
    namespace TEXT NOT NULL,
    name TEXT NOT NULL,
    version TEXT NOT NULL
);

CREATE TABLE vcs_info
(
    id BIGSERIAL PRIMARY KEY,
    type TEXT NOT NULL,
    url TEXT NOT NULL,
    revision TEXT NOT NULL,
    path TEXT NOT NULL
);

CREATE TABLE authors
(
    id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE processed_declared_licenses
(
    id BIGSERIAL PRIMARY KEY,
    license_spdx_id BIGINT NOT NULL,

    CONSTRAINT fk_license_spdx FOREIGN KEY (license_spdx_id) REFERENCES license_spdx(id)
);

CREATE TABLE projects
(
    id BIGSERIAL PRIMARY KEY,
    identifier_id BIGINT NOT NULL,
    vcs_id BIGINT NOT NULL,
    vcs_processed_id BIGINT NOT NULL,
    analyzer_run_id BIGINT NOT NULL,
    processed_declared_license_id BIGINT NULL,

    homepage_url TEXT NOT NULL,
    definition_file_path TEXT NOT NULL,
    cpe TEXT NULL,

    CONSTRAINT fk_projects_identifier FOREIGN KEY (identifier_id) REFERENCES identifiers(id),
    CONSTRAINT fk_projects_vcs FOREIGN KEY (vcs_id) REFERENCES vcs_info(id),
    CONSTRAINT fk_projects_vcs_processed FOREIGN KEY (vcs_processed_id) REFERENCES vcs_info(id),
    CONSTRAINT fk_projects_processed_declared_license
        FOREIGN KEY (processed_declared_license_id)
            REFERENCES processed_declared_licenses(id),
    CONSTRAINT fk_projects_analyzer_run FOREIGN KEY (analyzer_run_id) REFERENCES analyzer_runs(id)
);

CREATE TABLE projects_authors
(
    author_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,

    CONSTRAINT pk_projects_authors PRIMARY KEY (author_id, project_id),
    CONSTRAINT fk_author FOREIGN KEY (author_id) REFERENCES authors(id),
    CONSTRAINT fk_project FOREIGN KEY (project_id) REFERENCES projects(id)
);

CREATE TABLE projects_declared_licenses
(
    project_id BIGINT NOT NULL,
    license_string_id BIGINT NOT NULL,

    CONSTRAINT pk_projects_license_strings PRIMARY KEY (project_id, license_string_id),
    CONSTRAINT fk_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_license_string FOREIGN KEY (license_string_id) REFERENCES license_strings(id)
);

CREATE TABLE processed_declared_licenses_unmapped_licenses
(
    processed_declared_license_id BIGINT NOT NULL,
    license_string_id BIGINT NULL,

    CONSTRAINT pk_processed_declared_licenses_unmapped_licenses
        PRIMARY KEY (processed_declared_license_id, license_string_id),
    CONSTRAINT fk_processed_declared_license
        FOREIGN KEY (processed_declared_license_id)
            REFERENCES processed_declared_licenses(id),
    CONSTRAINT fk_license_string FOREIGN KEY (license_string_id) REFERENCES license_strings(id)
);

CREATE TABLE processed_declared_licenses_mapped_licenses
(
    id BIGSERIAL PRIMARY KEY,
    processed_declared_license_id BIGINT NOT NULL,
    license_string_id BIGINT NOT NULL,
    license_spdx_id BIGINT NOT NULL,

    CONSTRAINT fk_processed_declared_license
        FOREIGN KEY (processed_declared_license_id)
            REFERENCES processed_declared_licenses(id),
    CONSTRAINT fk_license_string FOREIGN KEY (license_string_id) REFERENCES license_strings(id),
    CONSTRAINT fk_license_spdx FOREIGN KEY (license_spdx_id) REFERENCES license_spdx(id)
);

CREATE TABLE project_scopes
(
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name TEXT NOT NULL,

    CONSTRAINT fk_project FOREIGN KEY (project_id) REFERENCES projects(id)
);
