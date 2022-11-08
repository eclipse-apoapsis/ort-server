CREATE TABLE curation_vcs_info
(
    id       BIGSERIAL PRIMARY KEY,
    type     TEXT NULL,
    url      TEXT NULL,
    revision TEXT NULL,
    path     TEXT NULL
);

CREATE TABLE remote_artifacts
(
    id             BIGSERIAL PRIMARY KEY,
    url            TEXT NOT NULL,
    hash_value     TEXT NOT NULL,
    hash_algorithm TEXT NOT NULL
);

CREATE TABLE package_curation_data
(
    id                        BIGSERIAL PRIMARY KEY,
    binary_artifact_id        BIGINT REFERENCES remote_artifacts  NULL,
    source_artifact_id        BIGINT REFERENCES remote_artifacts  NULL,
    vcs_id                    BIGINT REFERENCES curation_vcs_info NULL,
    concluded_license_spdx_id BIGINT REFERENCES license_spdx      NULL,

    purl                      TEXT                                NULL,
    cpe                       TEXT                                NULL,
    comment                   TEXT                                NULL,
    description               TEXT                                NULL,
    homepage_url              TEXT                                NULL,
    is_modified               BOOLEAN                             NULL,
    is_metadata_only          BOOLEAN                             NULL
);

CREATE TABLE package_curation_data_authors
(
    package_curation_data_id BIGINT REFERENCES package_curation_data NOT NULL,
    author_id                BIGINT REFERENCES authors               NOT NULL,

    PRIMARY KEY (package_curation_data_id, author_id)
);

CREATE TABLE package_curation_data_declared_license_mappings
(
    package_curation_data_id BIGINT REFERENCES package_curation_data NOT NULL,
    license_string_id        BIGINT REFERENCES license_strings       NOT NULL,
    license_spdx_id          BIGINT REFERENCES license_spdx          NOT NULL,

    PRIMARY KEY (package_curation_data_id, license_string_id, license_spdx_id)
);

CREATE TABLE packages
(
    id                            BIGSERIAL PRIMARY KEY,
    identifier_id                 BIGINT REFERENCES identifiers                 NOT NULL,
    vcs_id                        BIGINT REFERENCES vcs_info                    NOT NULL,
    vcs_processed_id              BIGINT REFERENCES vcs_info                    NOT NULL,
    binary_artifact_id            BIGINT REFERENCES remote_artifacts            NOT NULL,
    source_artifact_id            BIGINT REFERENCES remote_artifacts            NOT NULL,
    processed_declared_license_id BIGINT REFERENCES processed_declared_licenses NULL,
    concluded_license_spdx_id     BIGINT REFERENCES license_spdx                NULL,

    purl                          TEXT                                          NOT NULL,
    description                   TEXT                                          NOT NULL,
    homepage_url                  TEXT                                          NOT NULL,
    is_metadata_only              BOOLEAN DEFAULT FALSE                         NOT NULL,
    is_modified                   BOOLEAN DEFAULT FALSE                         NOT NULL,
    cpe                           TEXT                                          NULL
);

CREATE TABLE packages_declared_licenses
(
    package_id        BIGINT REFERENCES packages        NOT NULL,
    license_string_id BIGINT REFERENCES license_strings NOT NULL,

    PRIMARY KEY (package_id, license_string_id)
);

CREATE TABLE curated_packages
(
    id              BIGSERIAL PRIMARY KEY,
    package_id      BIGINT REFERENCES packages      NOT NULL,
    analyzer_run_id BIGINT REFERENCES analyzer_runs NOT NULL
);

CREATE TABLE packages_curation_results
(
    id                  BIGSERIAL PRIMARY KEY,
    base_curation_id    BIGINT REFERENCES package_curation_data NOT NULL,
    applied_curation_id BIGINT REFERENCES package_curation_data NOT NULL,
    curated_package_id  BIGINT REFERENCES curated_packages      NOT NULL
);

CREATE TABLE packages_authors
(
    author_id  BIGINT REFERENCES authors  NOT NULL,
    package_id BIGINT REFERENCES packages NOT NULL,

    PRIMARY KEY (author_id, package_id)
);
