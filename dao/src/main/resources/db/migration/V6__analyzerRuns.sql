CREATE TABLE environments
(
    id           bigserial PRIMARY KEY,
    ort_version  text   NOT NULL,
    java_version text   NOT NULL,
    os           text   NOT NULL,
    processors   int    NOT NULL,
    max_memory   bigint NOT NULL
);

CREATE TABLE environment_variables
(
    id             bigserial PRIMARY KEY,
    environment_id bigint REFERENCES environments ON DELETE CASCADE NOT NULL,
    name           text                                             NOT NULL,
    value          text                                             NOT NULL
);

CREATE TABLE environment_tool_versions
(
    id             bigserial PRIMARY KEY,
    environment_id bigint REFERENCES environments ON DELETE CASCADE NOT NULL,
    name           text                                             NULL,
    version        text                                             NULL
);

CREATE TABLE analyzer_runs
(
    id                        bigserial PRIMARY KEY,
    analyzer_job_id           bigint REFERENCES analyzer_jobs           NOT NULL,
    environment_id            bigint REFERENCES environments            NOT NULL,
    start_time                timestamp                                 NOT NULL,
    end_time                  timestamp                                 NOT NULL
);

CREATE TABLE analyzer_configurations
(
    id                        bigserial PRIMARY KEY,
    analyzer_run_id           bigint REFERENCES analyzer_runs ON DELETE CASCADE NOT NULL,
    allow_dynamic_versions    boolean                                           NOT NULL,
    enabled_package_managers  text                                              NULL,
    disabled_package_managers text                                              NULL
);

CREATE TABLE package_manager_configurations
(
    id                        bigserial PRIMARY KEY,
    analyzer_configuration_id bigint REFERENCES analyzer_configurations NOT NULL,
    name                      text                                      NOT NULL,
    must_run_after            text                                      NULL
);

CREATE TABLE package_manager_configuration_options
(
    id                               bigserial PRIMARY KEY,
    package_manager_configuration_id bigint REFERENCES package_manager_configurations NOT NULL,
    name                             text                                             NOT NULL,
    value                            text                                             NOT NULL
);
