-- Add a table to store events for the plugins enabled by default. A NULL organization ID denotes the global defaults.
CREATE TABLE default_plugins_events
(
    organization_id bigint REFERENCES organizations ON DELETE CASCADE NULL,
    category        text      NOT NULL,
    version         bigint    NOT NULL,
    payload         jsonb     NOT NULL,
    created_by      text      NOT NULL,
    created_at      timestamp NOT NULL DEFAULT now(),

    CONSTRAINT default_plugins_events_key UNIQUE NULLS NOT DISTINCT (organization_id, category, version)
);

-- Add a read model for the currently configured default plugins. A NULL organization ID denotes the global defaults.
CREATE TABLE default_plugins_read_model
(
    organization_id bigint REFERENCES organizations ON DELETE CASCADE NULL,
    category        text   NOT NULL,
    plugin_ids      text[] NOT NULL,

    CONSTRAINT default_plugins_read_model_key UNIQUE NULLS NOT DISTINCT (organization_id, category)
);
