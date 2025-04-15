CREATE TABLE plugins_read_model
(
    plugin_type text    NOT NULL,
    plugin_id   text    NOT NULL,
    enabled     boolean NOT NULL,

    PRIMARY KEY (plugin_type, plugin_id)
)
