-- The registry of projections, which are denormalized read models built for an ORT run on demand. Each row tracks the
-- build status of one projection type for one run and also serves as the work queue for building projections.
-- Projection tables reference this table, so that deleting a row here also deletes the data of the projection.

CREATE TABLE projection_registry
(
    ort_run_id       BIGINT    NOT NULL REFERENCES ort_runs ON DELETE CASCADE,
    projection_type  TEXT      NOT NULL,
    schema_version   INTEGER   NOT NULL,
    status           TEXT      NOT NULL,
    lease_owner      TEXT      NULL,
    lease_until      TIMESTAMP NULL,
    attempts         INTEGER   NOT NULL DEFAULT 0,
    next_attempt     TIMESTAMP NULL,
    error            TEXT      NULL,
    created_at       TIMESTAMP NOT NULL,
    last_accessed_at TIMESTAMP NOT NULL,

    PRIMARY KEY (ort_run_id, projection_type)
);

-- Used to find builds that need to be started or recovered.
CREATE INDEX projection_registry_status_idx ON projection_registry (status, created_at)
    WHERE status IN ('PENDING', 'BUILDING', 'FAILED');

-- Used to find projections that have not been accessed for a while.
CREATE INDEX projection_registry_last_accessed_at_idx ON projection_registry (last_accessed_at);
