-- Add a table for the issue resolutions read model.
CREATE TABLE issue_resolutions_read_model
(
    repository_id bigint NOT NULL,
    message_hash  text   NOT NULL,
    message       text   NOT NULL,
    reason        text   NOT NULL,
    comment       text   NOT NULL,

    PRIMARY KEY (repository_id, message_hash)
);
