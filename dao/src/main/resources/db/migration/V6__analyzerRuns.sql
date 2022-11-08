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
    environment_id BIGINT NOT NULL,
    name           TEXT   NOT NULL,
    value          TEXT   NOT NULL,

    CONSTRAINT fk_environment FOREIGN KEY (environment_id) REFERENCES environments (id)
);

CREATE TABLE environment_tool_versions
(
    id             BIGSERIAL PRIMARY KEY,
    environment_id BIGINT NOT NULL,
    name           TEXT,
    version        TEXT,

    CONSTRAINT fk_environment FOREIGN KEY (environment_id) REFERENCES environments (id)
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
    sw360_configuration_id BIGINT  NULL,
    allow_dynamic_versions BOOLEAN NOT NULL,

    CONSTRAINT fk_sw360_configuration FOREIGN KEY (sw360_configuration_id) REFERENCES sw360_configurations (id)
);

CREATE TABLE enabled_package_managers
(
    id                        BIGSERIAL PRIMARY KEY,
    analyzer_configuration_id BIGINT NOT NULL,
    package_manager           TEXT   NOT NULL,

    CONSTRAINT fk_analyzer_configuration FOREIGN KEY (analyzer_configuration_id) REFERENCES analyzer_configurations (id)
);

CREATE TABLE disabled_package_managers
(
    id                        BIGSERIAL PRIMARY KEY,
    analyzer_configuration_id BIGINT NOT NULL,
    package_manager           TEXT   NOT NULL,

    CONSTRAINT fk_analyzer_configuration FOREIGN KEY (analyzer_configuration_id) REFERENCES analyzer_configurations (id)
);

CREATE TABLE analyzer_runs
(
    id                        BIGSERIAL PRIMARY KEY,
    environment_id            BIGINT    NOT NULL,
    analyzer_configuration_id BIGINT    NOT NULL,
    start_time                TIMESTAMP NOT NULL,
    end_time                  TIMESTAMP NOT NULL,

    CONSTRAINT fk_environment FOREIGN KEY (environment_id) REFERENCES environments (id),
    CONSTRAINT fk_analyzer_configuration FOREIGN KEY (analyzer_configuration_id) REFERENCES analyzer_configurations (id)
);

CREATE TABLE package_manager_configurations
(
    id                        BIGSERIAL PRIMARY KEY,
    analyzer_configuration_id BIGINT NOT NULL,
    name                      TEXT   NOT NULL,

    CONSTRAINT fk_analyzer_configuration FOREIGN KEY (analyzer_configuration_id) REFERENCES analyzer_configurations (id)
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
    package_manager_configuration_id BIGINT NOT NULL,
    name                             TEXT   NOT NULL,

    CONSTRAINT fk_package_manager_configuration
        FOREIGN KEY (package_manager_configuration_id)
            REFERENCES package_manager_configurations (id)
);

CREATE TABLE package_manager_configurations_options
(
    package_manager_configuration_id BIGINT NOT NULL,
    option_id                        BIGINT NOT NULL,

    CONSTRAINT pk_package_manager_configurations_options
        PRIMARY KEY (package_manager_configuration_id, option_id),
    CONSTRAINT fk_package_manager_configuration
        FOREIGN KEY (package_manager_configuration_id)
            REFERENCES package_manager_configurations (id),
    CONSTRAINT fk_option
        FOREIGN KEY (option_id)
            REFERENCES options (id)
);
