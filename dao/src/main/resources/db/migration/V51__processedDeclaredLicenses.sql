CREATE TABLE processed_declared_licenses
(
    id              bigserial PRIMARY KEY,
    package_id      bigint REFERENCES packages NULL,
    project_id      bigint REFERENCES projects NULL,
    spdx_expression text                       NOT NULL,

    UNIQUE (id, package_id, spdx_expression)
);

CREATE TABLE mapped_declared_licenses
(
    id               bigserial PRIMARY KEY,
    declared_license text NOT NULL,
    mapped_license   text NOT NULL,

    UNIQUE (declared_license, mapped_license)
);

CREATE TABLE unmapped_declared_licenses
(
    id               bigserial PRIMARY KEY,
    unmapped_license text NOT NULL,

    UNIQUE (unmapped_license)
);

CREATE TABLE processed_declared_licenses_mapped_declared_licenses
(
    processed_declared_license_id bigint REFERENCES processed_declared_licenses NOT NULL,
    mapped_declared_license_id    bigint REFERENCES mapped_declared_licenses    NOT NULL,

    PRIMARY KEY (processed_declared_license_id, mapped_declared_license_id)
);

CREATE TABLE processed_declared_licenses_unmapped_declared_licenses
(
    processed_declared_license_id bigint REFERENCES processed_declared_licenses NOT NULL,
    unmapped_declared_license_id  bigint REFERENCES unmapped_declared_licenses  NOT NULL,

    PRIMARY KEY (processed_declared_license_id, unmapped_declared_license_id)
);
