-- Add a table for the rule violation resolutions read model.
CREATE TABLE rule_violation_resolutions_read_model
(
    repository_id bigint NOT NULL,
    message_hash  text   NOT NULL,
    message       text   NOT NULL,
    reason        text   NOT NULL,
    comment       text   NOT NULL,

    PRIMARY KEY (repository_id, message_hash)
);
