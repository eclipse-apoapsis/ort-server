CREATE TABLE curation_vcs_info
(
    id       bigserial PRIMARY KEY,
    type     text NULL,
    url      text NULL,
    revision text NULL,
    path     text NULL
);

CREATE TABLE remote_artifacts
(
    id             bigserial PRIMARY KEY,
    url            text NOT NULL,
    hash_value     text NOT NULL,
    hash_algorithm text NOT NULL
);

CREATE TABLE package_curation_data
(
    id                        bigserial PRIMARY KEY,
    binary_artifact_id        bigint REFERENCES remote_artifacts  NULL,
    source_artifact_id        bigint REFERENCES remote_artifacts  NULL,
    vcs_id                    bigint REFERENCES curation_vcs_info NULL,
    concluded_license_spdx_id bigint REFERENCES license_spdx      NULL,

    purl                      text                                NULL,
    cpe                       text                                NULL,
    comment                   text                                NULL,
    description               text                                NULL,
    homepage_url              text                                NULL,
    is_modified               boolean                             NULL,
    is_metadata_only          boolean                             NULL
);

CREATE TABLE package_curation_data_authors
(
    package_curation_data_id bigint REFERENCES package_curation_data NOT NULL,
    author_id                bigint REFERENCES authors               NOT NULL,

    PRIMARY KEY (package_curation_data_id, author_id)
);

CREATE TABLE package_curation_data_declared_license_mappings
(
    package_curation_data_id bigint REFERENCES package_curation_data NOT NULL,
    license_string_id        bigint REFERENCES license_strings       NOT NULL,
    license_spdx_id          bigint REFERENCES license_spdx          NOT NULL,

    PRIMARY KEY (package_curation_data_id, license_string_id, license_spdx_id)
);

CREATE TABLE packages
(
    id                        bigserial PRIMARY KEY,
    identifier_id             bigint REFERENCES identifiers      NOT NULL,
    vcs_id                    bigint REFERENCES vcs_info         NOT NULL,
    vcs_processed_id          bigint REFERENCES vcs_info         NOT NULL,
    binary_artifact_id        bigint REFERENCES remote_artifacts NOT NULL,
    source_artifact_id        bigint REFERENCES remote_artifacts NOT NULL,
    concluded_license_spdx_id bigint REFERENCES license_spdx     NULL,

    purl                      text                               NOT NULL,
    description               text                               NOT NULL,
    homepage_url              text                               NOT NULL,
    is_metadata_only          boolean DEFAULT FALSE              NOT NULL,
    is_modified               boolean DEFAULT FALSE              NOT NULL,
    cpe                       text                               NULL
);

CREATE TABLE packages_declared_licenses
(
    package_id        bigint REFERENCES packages        NOT NULL,
    license_string_id bigint REFERENCES license_strings NOT NULL,

    PRIMARY KEY (package_id, license_string_id)
);

CREATE TABLE curated_packages
(
    id              bigserial PRIMARY KEY,
    package_id      bigint REFERENCES packages      NOT NULL,
    analyzer_run_id bigint REFERENCES analyzer_runs NOT NULL
);

CREATE TABLE packages_curation_results
(
    id                  bigserial PRIMARY KEY,
    base_curation_id    bigint REFERENCES package_curation_data NOT NULL,
    applied_curation_id bigint REFERENCES package_curation_data NOT NULL,
    curated_package_id  bigint REFERENCES curated_packages      NOT NULL
);

CREATE TABLE packages_authors
(
    author_id  bigint REFERENCES authors  NOT NULL,
    package_id bigint REFERENCES packages NOT NULL,

    PRIMARY KEY (author_id, package_id)
);
