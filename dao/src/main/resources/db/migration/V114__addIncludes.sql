CREATE TABLE path_includes
(
    id      BIGSERIAL PRIMARY KEY,
    pattern TEXT NOT NULL,
    reason  TEXT NOT NULL,
    comment TEXT NOT NULL
);

CREATE TABLE repository_configurations_path_includes
(
    repository_configuration_id BIGINT NOT NULL,
    path_include_id             BIGINT NOT NULL,
    PRIMARY KEY (repository_configuration_id, path_include_id),
    CONSTRAINT fk_repository_configurations_path_includes_config_id
        FOREIGN KEY (repository_configuration_id)
            REFERENCES repository_configurations (id),
    CONSTRAINT fk_repository_configurations_path_includes_include_id
        FOREIGN KEY (path_include_id)
            REFERENCES path_includes (id)
);

CREATE INDEX idx_path_includes_compound
    ON path_includes (pattern, reason, comment);
