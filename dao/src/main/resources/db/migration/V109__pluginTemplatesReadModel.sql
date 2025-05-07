CREATE TABLE plugin_templates_read_model
(
    name             text    NOT NULL,
    plugin_type      text    NOT NULL,
    plugin_id        text    NOT NULL,
    options          jsonb   NOT NULL,
    is_global        boolean NOT NULL DEFAULT false,
    organization_ids bigint[] NOT NULL DEFAULT '{}',

    PRIMARY KEY (name, plugin_type, plugin_id)
)
