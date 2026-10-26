CREATE TABLE scope_includes
(
    id      bigserial PRIMARY KEY,
    pattern text NOT NULL,
    reason  text NOT NULL,
    comment text NOT NULL
);

CREATE INDEX scope_includes_all_value_columns
    ON scope_includes (pattern, reason, comment);

CREATE TABLE repository_configurations_scope_includes
(
    repository_configuration_id bigint REFERENCES repository_configurations ON DELETE CASCADE NOT NULL,
    scope_include_id            bigint REFERENCES scope_includes                              NOT NULL,

    PRIMARY KEY (repository_configuration_id, scope_include_id)
);
