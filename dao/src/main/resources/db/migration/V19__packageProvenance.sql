CREATE TABLE package_provenances
(
    id                bigserial PRIMARY KEY,
    identifier_id     bigint REFERENCES identifiers NOT NULL,
    artifact_id       bigint REFERENCES remote_artifacts NULL,
    vcs_id            bigint REFERENCES vcs_info NULL,
    is_fixed_revision boolean NULL,
    resolved_revision text NULL,
    cloned_revision   text NULL,
    error_message     text NULL,

    UNIQUE (identifier_id, artifact_id),
    UNIQUE (identifier_id, vcs_id)
);

