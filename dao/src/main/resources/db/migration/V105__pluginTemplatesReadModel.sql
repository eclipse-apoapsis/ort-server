CREATE TABLE plugin_templates_read_model
(
    name            text    NOT NULL,
    plugin_type     text    NOT NULL,
    plugin_id       text    NOT NULL,
    options         jsonb   NOT NULL,
    isGlobal        boolean NOT NULL,
    organizationIds bigint[] NOT NULL,

    PRIMARY KEY (plugin_type, plugin_id)
)
