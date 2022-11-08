CREATE TABLE environments
(
    id           BIGSERIAL PRIMARY KEY,
    ort_version  TEXT   NOT NULL,
    java_version TEXT   NOT NULL,
    os           TEXT   NOT NULL,
    processors   INT    NOT NULL,
    max_memory   BIGINT NOT NULL
);

CREATE TABLE environment_variables
(
    id             BIGSERIAL PRIMARY KEY,
    environment_id BIGINT REFERENCES environments NOT NULL,
    name           TEXT                           NOT NULL,
    value          TEXT                           NOT NULL
);

CREATE TABLE environment_tool_versions
(
    id             BIGSERIAL PRIMARY KEY,
    environment_id BIGINT REFERENCES environments NOT NULL,
    name           TEXT                           NULL,
    version        TEXT                           NULL
);

CREATE TABLE sw360_configurations
(
    id        BIGSERIAL PRIMARY KEY,
    rest_url  TEXT NOT NULL,
    auth_url  TEXT NOT NULL,
    username  TEXT NOT NULL,
    client_id TEXT NOT NULL
);

CREATE TABLE analyzer_configurations
(
    id                     BIGSERIAL PRIMARY KEY,
    sw360_configuration_id BIGINT REFERENCES sw360_configurations NULL,
    allow_dynamic_versions BOOLEAN                                NOT NULL
);

CREATE TABLE enabled_package_managers
(
    id                        BIGSERIAL PRIMARY KEY,
    analyzer_configuration_id BIGINT REFERENCES analyzer_configurations NOT NULL,
    package_manager           TEXT                                      NOT NULL
);

CREATE TABLE disabled_package_managers
(
    id                        BIGSERIAL PRIMARY KEY,
    analyzer_configuration_id BIGINT REFERENCES analyzer_configurations NOT NULL,
    package_manager           TEXT                                      NOT NULL
);

CREATE TABLE analyzer_runs
(
    id                        BIGSERIAL PRIMARY KEY,
    environment_id            BIGINT REFERENCES environments            NOT NULL,
    analyzer_configuration_id BIGINT REFERENCES analyzer_configurations NOT NULL,
    start_time                TIMESTAMP                                 NOT NULL,
    end_time                  TIMESTAMP                                 NOT NULL
);

CREATE TABLE package_manager_configurations
(
    id                        BIGSERIAL PRIMARY KEY,
    analyzer_configuration_id BIGINT REFERENCES analyzer_configurations NOT NULL,
    name                      TEXT                                      NOT NULL
);

CREATE TABLE options
(
    id    BIGSERIAL PRIMARY KEY,
    name  TEXT NOT NULL,
    value TEXT NOT NULL
);

CREATE TABLE package_manager_configurations_must_run_after
(
    id                               BIGSERIAL PRIMARY KEY,
    package_manager_configuration_id BIGINT REFERENCES package_manager_configurations NOT NULL,
    name                             TEXT                                             NOT NULL
);

CREATE TABLE package_manager_configurations_options
(
    package_manager_configuration_id BIGINT REFERENCES package_manager_configurations NOT NULL,
    option_id                        BIGINT REFERENCES options                        NOT NULL,

    PRIMARY KEY (package_manager_configuration_id, option_id)
);
