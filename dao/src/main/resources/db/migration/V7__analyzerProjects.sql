CREATE TABLE declared_licenses
(
    id   bigserial PRIMARY KEY,
    name text NOT NULL,

    UNIQUE (name)
);

CREATE TABLE identifiers
(
    id        bigserial PRIMARY KEY,
    type      text NOT NULL,
    namespace text NOT NULL,
    name      text NOT NULL,
    version   text NOT NULL,

    UNIQUE (type, namespace, name, version)
);

CREATE TABLE vcs_info
(
    id       bigserial PRIMARY KEY,
    type     text NOT NULL,
    url      text NOT NULL,
    revision text NOT NULL,
    path     text NOT NULL,

    UNIQUE (type, url, revision, path)
);

CREATE TABLE authors
(
    id   bigserial PRIMARY KEY,
    name text NOT NULL,

    UNIQUE (name)
);

CREATE TABLE projects
(
    id                   bigserial PRIMARY KEY,
    identifier_id        bigint REFERENCES identifiers   NOT NULL,
    vcs_id               bigint REFERENCES vcs_info      NOT NULL,
    vcs_processed_id     bigint REFERENCES vcs_info      NOT NULL,
    analyzer_run_id      bigint REFERENCES analyzer_runs NOT NULL,
    homepage_url         text                            NOT NULL,
    definition_file_path text                            NOT NULL,
    cpe                  text                            NULL
);

CREATE INDEX projects_all_value_columns ON projects (homepage_url, definition_file_path, cpe);

CREATE TABLE projects_authors
(
    author_id  bigint REFERENCES authors  NOT NULL,
    project_id bigint REFERENCES projects NOT NULL,

    PRIMARY KEY (author_id, project_id)
);

CREATE TABLE projects_declared_licenses
(
    project_id          bigint REFERENCES projects          NOT NULL,
    declared_license_id bigint REFERENCES declared_licenses NOT NULL,

    PRIMARY KEY (project_id, declared_license_id)
);

CREATE TABLE project_scopes
(
    id         bigserial PRIMARY KEY,
    project_id bigint REFERENCES projects NOT NULL,
    name       text                       NOT NULL
);
