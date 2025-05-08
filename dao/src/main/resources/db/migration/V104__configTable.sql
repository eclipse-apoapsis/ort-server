CREATE TABLE config_table
(
    key        text      PRIMARY KEY,
    value      text      NOT NULL,
    is_enabled boolean   DEFAULT FALSE,
    updated_at timestamp NOT NULL DEFAULT now()
);
