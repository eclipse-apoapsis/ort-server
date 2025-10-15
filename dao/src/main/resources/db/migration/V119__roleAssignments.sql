-- This migration creates the database structures to store role assignments for users to elements in the ORT Server
-- hierarchy.

CREATE TABLE role_assignments
(
    id                bigserial    PRIMARY KEY,
    user_id           text         NOT NULL,
    organization_id   bigint       REFERENCES organizations ON DELETE CASCADE NULL,
    product_id        bigint       REFERENCES products      ON DELETE CASCADE NULL,
    repository_id     bigint       REFERENCES repositories  ON DELETE CASCADE NULL,
    organization_role text         NULL,
    product_role      text         NULL,
    repository_role   text         NULL,

    UNIQUE (user_id, organization_id, product_id, repository_id)
);

-- Indexes for typical queries.
CREATE INDEX role_assignments_user_id
    ON role_assignments (user_id);

CREATE INDEX role_assignments_hierarchy
    ON role_assignments (organization_id, product_id, repository_id);

-- Foreign key indexes.
CREATE INDEX role_assignments_organization_id
    ON role_assignments (organization_id);

CREATE INDEX role_assignments_product_id
    ON role_assignments (product_id);

CREATE INDEX role_assignments_repository_id
    ON role_assignments (repository_id);

-- A single role assignment must reference exactly one role on an arbitrary level of the hierarchy.
ALTER TABLE role_assignments
ADD CONSTRAINT role_assignments_single_role_check
CHECK (
    (organization_role IS NOT NULL)::integer
    + (product_role IS NOT NULL)::integer
    + (repository_role IS NOT NULL)::integer
    = 1
);
