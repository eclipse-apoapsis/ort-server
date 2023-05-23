CREATE TABLE scanner_runs
(
    id             bigserial PRIMARY KEY,
    scanner_job_id bigint REFERENCES scanner_jobs NOT NULL,
    environment_id bigint REFERENCES environments NOT NULL,
    start_time     timestamp                      NOT NULL,
    end_time       timestamp                      NOT NULL
);

CREATE TABLE detected_license_mappings
(
    id           bigserial PRIMARY KEY,
    license      text NOT NULL,
    spdx_license text NOT NULL
);

CREATE TABLE scanner_configurations
(
    id                      bigserial PRIMARY KEY,
    scanner_run_id          bigint REFERENCES scanner_runs ON DELETE CASCADE NOT NULL,
    skip_concluded          boolean DEFAULT FALSE                            NOT NULL,
    create_missing_archives boolean DEFAULT FALSE                            NOT NULL,
    storage_readers         text NULL,
    storage_writers         text NULL,
    ignore_patterns         text NULL
);

CREATE TABLE scanner_configurations_scanner_options
(
    id                       bigserial PRIMARY KEY,
    scanner_configuration_id bigint REFERENCES scanner_configurations NOT NULL,
    scanner                  text                                     NOT NULL
);

CREATE TABLE scanner_configuration_options
(
    id                                      bigserial PRIMARY KEY,
    scanner_configuration_scanner_option_id bigint REFERENCES scanner_configurations_scanner_options NOT NULL,
    key                                     text                                                     NOT NULL,
    value                                   text                                                     NOT NULL
);

CREATE TABLE scanner_configurations_detected_license_mappings
(
    scanner_configuration_id    bigint REFERENCES scanner_configurations    NOT NULL,
    detected_license_mapping_id bigint REFERENCES detected_license_mappings NOT NULL,

    PRIMARY KEY (scanner_configuration_id, detected_license_mapping_id)
);

CREATE TABLE scanner_configurations_storages
(
    id                       bigserial PRIMARY KEY,
    scanner_configuration_id bigint REFERENCES scanner_configurations NOT NULL,
    storage                  text                                     NOT NULL
);

CREATE TABLE sw360_storage_configurations
(
    id        bigserial PRIMARY KEY,
    rest_url  text NOT NULL,
    auth_url  text NOT NULL,
    username  text NOT NULL,
    client_id text NOT NULL
);

CREATE TABLE clearly_defined_storage_configurations
(
    id         bigserial PRIMARY KEY,
    server_url TEXT NOT NULL,

    UNIQUE (server_url)
);

CREATE TABLE postgres_connections
(
    id                    bigserial PRIMARY KEY,
    url                   text NOT NULL,
    schema                text NOT NULL,
    username              text NOT NULL,
    ssl_mode              text NOT NULL,
    ssl_cert              text NULL,
    ssl_key               text NULL,
    ssl_root_cert         text NULL,
    parallel_transactions int  NOT NULL
);

CREATE TABLE http_file_storage_configurations
(
    id    bigserial PRIMARY KEY,
    url   text NOT NULL,
    query text NULL
);

CREATE TABLE http_file_storage_configuration_headers
(
    id                                 bigserial PRIMARY KEY,
    http_file_storage_configuration_id bigint REFERENCES http_file_storage_configurations NOT NULL,
    key                                text                                               NOT NULL,
    value                              text                                               NOT NULL
);

CREATE TABLE local_file_storage_configurations
(
    id          bigserial PRIMARY KEY,
    directory   text                 NOT NULL,
    compression boolean DEFAULT TRUE NOT NULL
);

CREATE TABLE file_storage_configurations
(
    id                                  bigserial PRIMARY KEY,
    http_file_storage_configuration_id  bigint REFERENCES http_file_storage_configurations NULL,
    local_file_storage_configuration_id bigint REFERENCES local_file_storage_configurations NULL
);

CREATE TABLE file_based_storage_configurations
(
    id                            bigserial PRIMARY KEY,
    file_storage_configuration_id bigint REFERENCES file_storage_configurations NOT NULL,
    type                          text                                          NOT NULL
);

CREATE TABLE postgres_storage_configurations
(
    id                     bigserial PRIMARY KEY,
    postgres_connection_id bigint REFERENCES postgres_connections NOT NULL,
    type                   text                                   NOT NULL
);

CREATE TABLE storage_configurations
(
    id                                       bigserial PRIMARY KEY,
    scanner_configuration_storage_id         bigint REFERENCES scanner_configurations_storages NOT NULL,
    clearly_defined_storage_configuration_id bigint REFERENCES clearly_defined_storage_configurations NULL,
    file_based_storage_configuration_id      bigint REFERENCES file_based_storage_configurations NULL,
    postgres_storage_configuration_id        bigint REFERENCES postgres_storage_configurations NULL,
    sw360_storage_configuration_id           bigint REFERENCES sw360_storage_configurations NULL
);

CREATE TABLE file_archiver_configurations
(
    id                                bigserial PRIMARY KEY,
    scanner_configuration_id          bigint REFERENCES scanner_configurations NOT NULL,
    file_storage_configuration_id     bigint REFERENCES file_storage_configurations NULL,
    postgres_storage_configuration_id bigint REFERENCES postgres_storage_configurations NULL,
    enabled                           boolean DEFAULT TRUE                     NOT NULL
);

CREATE TABLE provenance_storage_configurations
(
    id                                bigserial PRIMARY KEY,
    scanner_configuration_id          bigint REFERENCES scanner_configurations NOT NULL,
    file_storage_configuration_id     bigint REFERENCES file_storage_configurations NULL,
    postgres_storage_configuration_id bigint REFERENCES postgres_storage_configurations NULL
);
