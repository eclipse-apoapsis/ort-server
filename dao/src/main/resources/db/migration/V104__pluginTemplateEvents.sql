CREATE TABLE plugin_template_events
(
    name        text      NOT NULL,
    plugin_type text      NOT NULL,
    plugin_id   text      NOT NULL,
    version     bigint    NOT NULL,
    payload     jsonb     NOT NULL,
    created_by  text      NOT NULL,
    created_at  timestamp NOT NULL DEFAULT now(),

    PRIMARY KEY (name, plugin_type, plugin_id, version)
);
