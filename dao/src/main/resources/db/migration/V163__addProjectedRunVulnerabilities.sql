-- The projection of the vulnerabilities of an ORT run, with one row for each combination of a vulnerability, an affected
-- package, and the advisor that reported it. Values that are expensive to compute on every request, like the rating, the
-- purl, and the resolution status, are stored in their own columns, so that they can be filtered and sorted.
-- Resolutions managed on the server can change after the run has finished, so they are not stored here.

CREATE TABLE projected_run_vulnerabilities
(
    ort_run_id               BIGINT  NOT NULL,
    projection_type          TEXT    NOT NULL DEFAULT 'run-vulnerabilities'
        CHECK (projection_type = 'run-vulnerabilities'),
    vulnerability_id         BIGINT  NOT NULL,
    identifier_id            BIGINT  NOT NULL,
    advisor_name             TEXT    NOT NULL,
    external_id              TEXT    NOT NULL,
    summary                  TEXT    NULL,
    description              TEXT    NULL,
    rating                   INTEGER NOT NULL,
    purl                     TEXT    NOT NULL,
    identifier_type          TEXT    NOT NULL,
    identifier_namespace     TEXT    NOT NULL,
    identifier_name          TEXT    NOT NULL,
    identifier_version       TEXT    NOT NULL,
    first_fixed_versions     JSONB   NOT NULL,
    fix_available            BOOLEAN NOT NULL,
    vulnerability_references JSONB   NOT NULL,
    resolutions              JSONB   NOT NULL,
    resolved                 BOOLEAN NOT NULL,

    PRIMARY KEY (ort_run_id, vulnerability_id, identifier_id, advisor_name),
    FOREIGN KEY (ort_run_id, projection_type) REFERENCES projection_registry ON DELETE CASCADE
)
-- Each build and each cleanup inserts or deletes all rows of a run at once. With the default scale factor of 20%,
-- autovacuum would run late on a large table, so the table is vacuumed and analyzed when about 2% of its rows changed:
-- - Vacuuming after deletes removes the dead rows and limits the bloat of the table and its indexes.
-- - Vacuuming after inserts updates the visibility map, so that counting the rows of a run can use an index-only scan.
-- - Analyzing after a build keeps the statistics up to date for the query planner.
WITH (
    autovacuum_vacuum_scale_factor = 0.02,
    autovacuum_vacuum_insert_scale_factor = 0.02,
    autovacuum_analyze_scale_factor = 0.02
);

CREATE INDEX projected_run_vulnerabilities_rating_idx ON projected_run_vulnerabilities (ort_run_id, rating);
CREATE INDEX projected_run_vulnerabilities_external_id_idx ON projected_run_vulnerabilities (ort_run_id, external_id);
CREATE INDEX projected_run_vulnerabilities_purl_idx ON projected_run_vulnerabilities (ort_run_id, purl);
CREATE INDEX projected_run_vulnerabilities_identifier_idx ON projected_run_vulnerabilities
    (ort_run_id, identifier_type, identifier_namespace, identifier_name, identifier_version);
