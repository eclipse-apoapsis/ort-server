CREATE TABLE remote_artifacts
(
    id             bigserial PRIMARY KEY,
    url            text NOT NULL,
    hash_value     text NOT NULL,
    hash_algorithm text NOT NULL
);

CREATE TABLE packages
(
    id                 bigserial PRIMARY KEY,
    identifier_id      bigint REFERENCES identifiers      NOT NULL,
    vcs_id             bigint REFERENCES vcs_info         NOT NULL,
    vcs_processed_id   bigint REFERENCES vcs_info         NOT NULL,
    binary_artifact_id bigint REFERENCES remote_artifacts NOT NULL,
    source_artifact_id bigint REFERENCES remote_artifacts NOT NULL,

    purl               text                               NOT NULL,
    cpe                text                               NULL,
    description        text                               NOT NULL,
    homepage_url       text                               NOT NULL,
    is_metadata_only   boolean DEFAULT FALSE              NOT NULL,
    is_modified        boolean DEFAULT FALSE              NOT NULL
);

CREATE TABLE packages_analyzer_runs
(
    package_id      bigint REFERENCES packages      NOT NULL,
    analyzer_run_id bigint REFERENCES analyzer_runs NOT NULL,

    PRIMARY KEY (package_id, analyzer_run_id)
);

CREATE TABLE packages_declared_licenses
(
    package_id          bigint REFERENCES packages          NOT NULL,
    declared_license_id bigint REFERENCES declared_licenses NOT NULL,

    PRIMARY KEY (package_id, declared_license_id)
);

CREATE TABLE packages_authors
(
    author_id  bigint REFERENCES authors  NOT NULL,
    package_id bigint REFERENCES packages NOT NULL,

    PRIMARY KEY (author_id, package_id)
);
