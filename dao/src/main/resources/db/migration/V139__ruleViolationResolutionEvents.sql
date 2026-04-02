-- Add a table to store rule violation resolution events.
CREATE TABLE rule_violation_resolution_events
(
    repository_id bigint    NOT NULL,
    message_hash  text      NOT NULL,
    version       bigint    NOT NULL,
    payload       jsonb     NOT NULL,
    created_by    text      NOT NULL,
    created_at    timestamp NOT NULL DEFAULT now(),

    PRIMARY KEY (repository_id, message_hash, version)
);
