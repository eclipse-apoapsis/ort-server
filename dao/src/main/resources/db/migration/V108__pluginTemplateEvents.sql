-- Add a table to store plugin template events.
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

-- Add a table to ensure the cross-aggregate constraint that only one plugin template for a given plugin type and ID can
-- be assigned to an organization at the same time.
CREATE TABLE plugin_template_organization_assignments
(
    plugin_type     text   NOT NULL,
    plugin_id       text   NOT NULL,
    organization_id bigint NOT NULL,
    template_name   text   NOT NULL,

    PRIMARY KEY (plugin_type, plugin_id, organization_id)
);
