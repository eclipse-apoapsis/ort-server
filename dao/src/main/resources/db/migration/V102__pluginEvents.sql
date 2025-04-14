CREATE TABLE plugin_events
(
    plugin_type text      NOT NULL,
    plugin_id   text      NOT NULL,
    version     bigint    NOT NULL,
    payload     jsonb     NOT NULL,
    created_at  timestamp NOT NULL DEFAULT now(),
    created_by  text      NOT NULL,

    PRIMARY KEY (plugin_type, plugin_id, version)
);
