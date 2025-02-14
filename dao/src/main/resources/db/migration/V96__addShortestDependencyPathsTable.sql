-- This migration adds a table for the shortest dependency path for a package.

CREATE TABLE shortest_dependency_paths
(
    id              bigserial PRIMARY KEY,
    package_id      bigint REFERENCES packages                          NOT NULL,
    analyzer_run_id bigint REFERENCES analyzer_runs ON DELETE CASCADE   NOT NULL,
    project_id      bigint REFERENCES projects                          NOT NULL,
    scope           text                                                NOT NULL,
    path            jsonb                                               NOT NULL
);
