-- This migration converts the 1:n relation between projects and analyzer runs to a n:m relation, so that
-- project entities can be shared between multiple analyzer runs.

CREATE TABLE projects_analyzer_runs
(
    project_id      bigint REFERENCES projects      NOT NULL,
    analyzer_run_id bigint REFERENCES analyzer_runs NOT NULL,

    PRIMARY KEY (project_id, analyzer_run_id)
);

INSERT INTO projects_analyzer_runs
SELECT
  p.id,
  p.analyzer_run_id
FROM
  projects p;

ALTER TABLE projects
DROP COLUMN analyzer_run_id;

-- The following statements make sure that all packages and projects are associated with a processed license.
-- This is necessary for old databases; due to a bug in the past, some packages and projects do not have a processed
-- license assigned. This causes the de-duplication logic for packages and projects to fail.

INSERT INTO processed_declared_licenses (package_id, project_id, spdx_expression)
SELECT
  p.id,
  NULL,
  'NOASSERTION'
FROM
  packages p, packages_analyzer_runs par, analyzer_runs ar
WHERE NOT EXISTS (
  SELECT 1
  FROM processed_declared_licenses pdl
  WHERE pdl.package_id = p.id
)
AND par.analyzer_run_id = ar.id
AND par.package_id = p.id;

INSERT INTO processed_declared_licenses (package_id, project_id, spdx_expression)
SELECT
  NULL,
  p.id,
  'NOASSERTION'
FROM
  projects p
WHERE NOT EXISTS (
  SELECT 1
  FROM processed_declared_licenses pdl
  WHERE pdl.project_id = p.id
);
