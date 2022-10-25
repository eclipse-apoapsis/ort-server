CREATE TABLE curation_vcs_info
(
    id BIGSERIAL PRIMARY KEY,
    type TEXT NULL,
    url TEXT NULL,
    revision TEXT NULL,
    path TEXT NULL
);

CREATE TABLE remote_artifacts
(
    id BIGSERIAL PRIMARY KEY,
    url TEXT NOT NULL,
    hash_value TEXT NOT NULL,
    hash_algorithm TEXT NOT NULL
);

CREATE TABLE package_curation_data
(
    id BIGSERIAL PRIMARY KEY,
    binary_artifact_id BIGINT NULL,
    source_artifact_id BIGINT NULL,
    vcs_id BIGINT NULL,
    concluded_license_spdx_id BIGINT NULL,

    purl TEXT NULL,
    cpe TEXT NULL,
    comment TEXT NULL,
    description TEXT NULL,
    homepage_url TEXT NULL,
    is_modified BOOLEAN NULL,
    is_metadata_only BOOLEAN NULL,

    CONSTRAINT fk_package_curation_data_vcs
        FOREIGN KEY (vcs_id)
            REFERENCES curation_vcs_info(id),
    CONSTRAINT fk_package_curation_data_binary_artifact
        FOREIGN KEY (binary_artifact_id)
            REFERENCES remote_artifacts(id),
    CONSTRAINT fk_package_curation_data_source_artifact
        FOREIGN KEY (source_artifact_id)
            REFERENCES remote_artifacts(id),
    CONSTRAINT fk_package_curation_data_concluded_license_spdx
        FOREIGN KEY (concluded_license_spdx_id)
            REFERENCES license_spdx(id)
);

CREATE TABLE package_curation_data_authors
(
    package_curation_data_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,

    CONSTRAINT pk_package_curation_data_author
        PRIMARY KEY (package_curation_data_id, author_id),
    CONSTRAINT fk_package_curation_data
        FOREIGN KEY (package_curation_data_id)
            REFERENCES package_curation_data(id),
    CONSTRAINT fk_author
        FOREIGN KEY (author_id)
            REFERENCES authors(id)
);

CREATE TABLE package_curation_data_declared_license_mappings
(
    package_curation_data_id BIGINT NOT NULL,
    license_string_id BIGINT NOT NULL,
    license_spdx_id BIGINT NOT NULL,

    CONSTRAINT pk_package_curation_data_declared_license_mappings
        PRIMARY KEY (package_curation_data_id, license_string_id, license_spdx_id),
    CONSTRAINT fk_package_curation_data
        FOREIGN KEY (package_curation_data_id)
            REFERENCES package_curation_data(id),
    CONSTRAINT fk_license_string
        FOREIGN KEY (license_string_id)
            REFERENCES license_strings(id),
    CONSTRAINT fk_license_spdx
        FOREIGN KEY (license_spdx_id)
            REFERENCES license_spdx(id)
);

CREATE TABLE packages
(
    id BIGSERIAL PRIMARY KEY,
    identifier_id BIGINT NOT NULL,
    vcs_id BIGINT NOT NULL,
    vcs_processed_id BIGINT NOT NULL,
    binary_artifact_id BIGINT NOT NULL,
    source_artifact_id BIGINT NOT NULL,
    processed_declared_license_id BIGINT NULL,
    concluded_license_spdx_id BIGINT NULL,

    purl TEXT NOT NULL,
    description TEXT NOT NULL,
    homepage_url TEXT NOT NULL,
    is_metadata_only BOOLEAN DEFAULT FALSE,
    is_modified BOOLEAN DEFAULT FALSE,
    cpe TEXT NULL,

    CONSTRAINT fk_package_identifier
        FOREIGN KEY (identifier_id)
            REFERENCES identifiers(id),
    CONSTRAINT fk_package_vcs
        FOREIGN KEY (vcs_id)
            REFERENCES vcs_info(id),
    CONSTRAINT fk_package_vcs_processed
        FOREIGN KEY (vcs_processed_id)
            REFERENCES vcs_info(id),
    CONSTRAINT fk_package_binary_artifact
        FOREIGN KEY (binary_artifact_id)
            REFERENCES remote_artifacts(id),
    CONSTRAINT fk_package_source_artifact
        FOREIGN KEY (source_artifact_id)
            REFERENCES remote_artifacts(id),
    CONSTRAINT fk_package_processed_declared_license
        FOREIGN KEY (processed_declared_license_id)
            REFERENCES processed_declared_licenses(id),
    CONSTRAINT fk_package_concluded_license_spdx
        FOREIGN KEY (concluded_license_spdx_id)
            REFERENCES license_spdx(id)
);

CREATE TABLE packages_declared_licenses
(
    package_id BIGINT NOT NULL,
    license_string_id BIGINT NOT NULL,

    CONSTRAINT pk_packages_license_strings
        PRIMARY KEY (package_id, license_string_id),
    CONSTRAINT fk_package
        FOREIGN KEY (package_id)
            REFERENCES packages(id),
    CONSTRAINT fk_license_string
        FOREIGN KEY (license_string_id)
            REFERENCES license_strings(id)
);

CREATE TABLE curated_packages
(
    id BIGSERIAL PRIMARY KEY,
    package_id BIGINT NOT NULL,
    analyzer_run_id BIGINT NOT NULL,

    CONSTRAINT fk_package
        FOREIGN KEY (package_id)
            REFERENCES packages(id),
    CONSTRAINT fk_analyzer_run
        FOREIGN KEY (analyzer_run_id)
            REFERENCES analyzer_runs(id)
);

CREATE TABLE packages_curation_results
(
    base_curation_id BIGINT NOT NULL,
    applied_curation_id BIGINT NOT NULL,
    curated_package_id BIGINT NOT NULL,

    CONSTRAINT pk_packages_curation_results_data
        PRIMARY KEY (base_curation_id, applied_curation_id, curated_package_id),
    CONSTRAINT fk_base_curation
        FOREIGN KEY (base_curation_id)
            REFERENCES package_curation_data(id),
    CONSTRAINT fk_applied_curation
        FOREIGN KEY (applied_curation_id)
            REFERENCES package_curation_data(id),
    CONSTRAINT fk_curated_package
        FOREIGN KEY (curated_package_id)
            REFERENCES curated_packages(id)
);

CREATE TABLE packages_authors
(
    author_id BIGINT NOT NULL,
    package_id BIGINT NOT NULL,

    CONSTRAINT pk_packages_authors
        PRIMARY KEY (author_id, package_id),
    CONSTRAINT fk_author
        FOREIGN KEY (author_id)
            REFERENCES authors(id),
    CONSTRAINT fk_package
        FOREIGN KEY (package_id)
            REFERENCES packages(id)
);
