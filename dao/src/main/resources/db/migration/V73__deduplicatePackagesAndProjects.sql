-- This migration script removes duplicate entries from the packages and projects tables and updates the references
-- to these entities accordingly.

-- The algorithm works in multiple steps:
-- 1. Compute hash values for packages and projects based on their properties. So, duplicate entities are assigned the
--    same hash value.
-- 2. Replace the link tables between analyzer runs and packages/projects with new tables that use the hash to
--    reference the entities.
-- 3. Now the duplicate projects and packages can be removed.
-- 4. Recreate the link tables based on the hash values which are now unique.

-- Step 1: Computation of hash values.
-- This is in theory straight-forward. However, because of the relations to authors, licenses, and processed licenses,
-- it becomes quite tricky. To deal with these relations, the string_agg() function is used to combine the values
-- from the relations into single strings. To simplify this, the script creates a number of temporary helper views.
-- For the relation to processed licenses, this trick has to be applied even one level deeper, since the table
-- processed_declared_licenses has further relations to mapped and unmapped declared licenses.

-- Helper views for processed declared licenses.

-- A view that assigns processed declared licenses the mapped licenses as a string.
-- The UNION is needed to prevent that processed declared licenses without mapped licenses are dropped.
CREATE VIEW processed_declared_licenses_with_mapped_licenses AS
SELECT
	pdl.id,
	string_agg(mdl.declared_license, ',' ORDER BY mdl.declared_license) declared,
	string_agg(mdl.mapped_license, ',' ORDER BY	mdl.mapped_license) mapped
FROM
	processed_declared_licenses pdl
INNER JOIN processed_declared_licenses_mapped_declared_licenses pdlmdl ON
	pdlmdl.processed_declared_license_id = pdl.id
INNER JOIN mapped_declared_licenses mdl ON
	pdlmdl.mapped_declared_license_id = mdl.id
GROUP BY
	pdl.id
UNION
SELECT
	pdl.id,
	'',
	''
FROM
	processed_declared_licenses pdl
where
	NOT EXISTS (
	SELECT
		1
	FROM
		processed_declared_licenses_mapped_declared_licenses pdlmdl
	where
		pdlmdl.processed_declared_license_id = pdl.id
);

-- A view that assigns processed declared licenses the unmapped licenses as a string.
CREATE VIEW processed_declared_licenses_with_unmapped_licenses as
SELECT
	pdl.id,
	string_agg(udl.unmapped_license, ',' ORDER BY udl.unmapped_license) unmapped
FROM
	processed_declared_licenses pdl
INNER JOIN processed_declared_licenses_unmapped_declared_licenses pdludl ON
	pdludl.processed_declared_license_id = pdl.id
INNER JOIN unmapped_declared_licenses udl ON
	pdludl.unmapped_declared_license_id = udl.id
GROUP BY
	pdl.id
UNION
SELECT
	pdl.id,
	''
FROM
	processed_declared_licenses pdl
where
	NOT EXISTS (
	SELECT
		1
	FROM
		processed_declared_licenses_unmapped_declared_licenses pdludl2
	where
		pdludl2.processed_declared_license_id = pdl.id
);

-- A view that assigns processed declared licenses a hash value computed from the properties of the table and
-- the views for the relations.
CREATE VIEW processed_declared_licenses_with_hashes AS
SELECT
	pdl.id,
  	encode(sha256(concat(
  pdl.spdx_expression,
	pdlml.declared,
	pdlml.mapped,
	pdlul.unmapped
  )::bytea),
	'hex') as hash
FROM
	processed_declared_licenses pdl
INNER JOIN processed_declared_licenses_with_mapped_licenses pdlml ON pdl.id = pdlml.id
INNER JOIN processed_declared_licenses_with_unmapped_licenses pdlul ON pdl.id = pdlul.id;

-- Helper views for packages.
-- There is a view for each relation that exposes the values from the relation as a concatenated string.
-- Then there is a view with the hash values for packages.

-- A view with the licenses assigned to a package as a single string.
CREATE VIEW packages_with_licenses AS
SELECT
  p.id,
  string_agg(dl.name, ',' ORDER BY dl.name) licenses
FROM
  packages p
INNER JOIN packages_declared_licenses pdl ON pdl.package_id = p.id
INNER JOIN declared_licenses dl ON pdl.declared_license_id = dl.id
GROUP BY
  p.id
UNION
SELECT
  p.id,
  '' as licenses
FROM
  packages p
where
  NOT EXISTS (
  SELECT
    1
  FROM
    packages_declared_licenses pdl
  WHERE
    pdl.package_id = p.id
);

-- A view with the processed licenses assigned to a package as a single string.
CREATE VIEW packages_with_processed_licenses AS
SELECT
  p.id,
  string_agg(pdl2.hash, ',' ORDER BY pdl2.hash) processed_license_hash
FROM
  packages p
INNER JOIN processed_declared_licenses pdl3 ON pdl3.package_id = p.id  
INNER JOIN processed_declared_licenses_with_hashes pdl2 ON pdl3.id = pdl2.id
GROUP BY
  p.id
UNION
SELECT
  p.id,
  '' as processed_license_hash
FROM
  packages p
where
  NOT EXISTS (
  SELECT
    1
  FROM
    processed_declared_licenses pdl
  WHERE
    pdl.package_id = p.id
);

-- A view with the authors assigned to a package as a single string.
CREATE VIEW packages_with_authors AS
SELECT
  p.id,
  string_agg(a."name", ',' ORDER BY a."name") authors
FROM
  packages p
INNER JOIN packages_authors pa ON pa.package_id = p.id
INNER JOIN authors a ON pa.author_id = a.id
GROUP BY
  p.id
UNION 
SELECT
  p.id,
  '' as authors
FROM
  packages p
where
  NOT EXISTS (
  SELECT
    1
  FROM
    packages_authors pa
  WHERE
    pa.package_id = p.id
);

-- A view that assigns each package a hash value computed from its properties and relations.
CREATE VIEW packages_with_hashes AS
SELECT
  p.id,
  encode(sha256(convert_to(concat(
    p.identifier_id,
    p.vcs_id,
    p.vcs_processed_id,
    p.binary_artifact_id,
    p.source_artifact_id,
    p.purl,
    p.cpe,
    p.description,
    p.homepage_url,
    p.is_metadata_only,
    p.is_modified,
    pl.licenses,
    pa.authors,
    ppl.processed_license_hash
  ), 'UTF8')),
    'hex') AS hash
FROM
  packages p
INNER JOIN packages_with_licenses pl ON p.id = pl.id
INNER JOIN packages_with_authors pa ON p.id = pa.id
INNER JOIN packages_with_processed_licenses ppl ON p.id = ppl.id;

-- A temporary table that contains only the IDs of the projects that remain after the deduplication.
CREATE TABLE deduplicated_packages
(
    id bigserial PRIMARY KEY
);

INSERT INTO deduplicated_packages
SELECT
  MIN(pwh.id) id
FROM
  packages_with_hashes pwh
GROUP BY pwh.hash;

-- Helper views for projects.

-- A view with the licenses assigned to a project as a single string.
CREATE VIEW projects_with_licenses AS
SELECT
  p.id,
  string_agg(dl.name, ',' ORDER BY dl.name) licenses
FROM
  projects p
INNER JOIN projects_declared_licenses pdl ON pdl.project_id = p.id
INNER JOIN declared_licenses dl ON pdl.declared_license_id = dl.id
GROUP BY
  p.id
UNION
SELECT
  p.id,
  '' as licenses
FROM
  projects p
where
  NOT EXISTS (
  SELECT
    1
  FROM
    projects_declared_licenses pdl
  WHERE
    pdl.project_id = p.id
);

-- A view with the processed licenses assigned to a project as a single string.
CREATE VIEW projects_with_processed_licenses AS
SELECT
  p.id,
  string_agg(pdl2.hash, ',' ORDER BY pdl2.hash) processed_license_hash
FROM
  projects p
INNER JOIN processed_declared_licenses pdl3 ON pdl3.project_id = p.id
INNER JOIN processed_declared_licenses_with_hashes pdl2 ON pdl3.id = pdl2.id
GROUP BY
  p.id
UNION
SELECT
  p.id,
  '' as processed_license_hash
FROM
  projects p
where
  NOT EXISTS (
  SELECT
    1
  FROM
    processed_declared_licenses pdl
  WHERE
    pdl.project_id = p.id
);

-- A view with the authors assigned to a project as a single string.
CREATE VIEW projects_with_authors AS
SELECT
  p.id,
  string_agg(a."name", ',' order by a."name") authors
FROM
  projects p
INNER JOIN projects_authors pa ON pa.project_id = p.id
INNER JOIN authors a ON	pa.author_id = a.id
GROUP BY
  p.id
UNION
SELECT
  p.id,
  '' as authors
FROM
  projects p
WHERE
  NOT EXISTS (
  SELECT
    1
  FROM
    projects_authors pa
  WHERE
    pa.project_id = p.id
);

-- A view with the scopes assigned to a project as a single string.
CREATE VIEW projects_with_scopes AS
SELECT
  p.id,
  string_agg(s."name", ',' order by s."name") scopes
FROM
  projects p
INNER JOIN project_scopes s ON s.project_id = p.id
GROUP BY p.id
UNION
SELECT
  p.id,
  '' as scopes
FROM
  projects p
WHERE
  NOT EXISTS (
  SELECT
    1
  FROM
    project_scopes ps
  WHERE
    ps.project_id = p.id
);

-- A view that assigns each project a hash value computed from its properties and relations.
CREATE VIEW projects_with_hashes AS
SELECT
  p.id,
  encode(sha256(convert_to(concat(
    p.identifier_id,
    p.vcs_id,
    p.vcs_processed_id,
    p.cpe,
    p.homepage_url,
    p.definition_file_path,
    pl.licenses,
    pa.authors,
    ps.scopes,
    ppl.processed_license_hash
  ), 'UTF8')),
    'hex') AS hash
FROM
  projects p
INNER JOIN projects_with_licenses pl ON p.id = pl.id
INNER JOIN projects_with_authors pa ON p.id = pa.id
INNER JOIN projects_with_scopes ps ON p.id = ps.id
INNER JOIN projects_with_processed_licenses ppl ON p.id = ppl.id;

-- A temporary table that contains only the IDs of the projects that remain after the deduplication.
CREATE TABLE deduplicated_projects
(
    id bigserial PRIMARY KEY
);

INSERT INTO deduplicated_projects
SELECT
  MIN(pwh.id) id
FROM
  projects_with_hashes pwh
GROUP BY pwh.hash;

-- Step 2: Create link tables using hashes instead of IDs.

CREATE TABLE packages_analyzer_runs_hashes
(
    analyzer_run_id bigint REFERENCES analyzer_runs NOT NULL,
    hash    text                                    NOT NULL,

    PRIMARY KEY (analyzer_run_id, hash)
);

INSERT INTO
  packages_analyzer_runs_hashes
SELECT
  par.analyzer_run_id,
  ph.hash
FROM
  packages_analyzer_runs par
INNER JOIN packages_with_hashes ph ON package_id = ph.id;

DELETE FROM packages_analyzer_runs;

CREATE TABLE projects_analyzer_runs_hashes
(
    analyzer_run_id bigint REFERENCES analyzer_runs NOT NULL,
    hash    text                                    NOT NULL,

    PRIMARY KEY (analyzer_run_id, hash)
);

INSERT INTO
  projects_analyzer_runs_hashes
SELECT
  par.analyzer_run_id,
  ph.hash
FROM
  projects_analyzer_runs par
INNER JOIN projects_with_hashes ph ON project_id = ph.id;

DELETE FROM projects_analyzer_runs;

-- Step 3: Remove duplicates.

DELETE FROM
  packages_authors pa
WHERE pa.package_id NOT IN (
  SELECT
    d.id
  FROM
    deduplicated_packages d
);

DELETE FROM
  packages_declared_licenses pdl
WHERE pdl.package_id NOT IN (
  SELECT
    d.id
  FROM
    deduplicated_packages d
);

DELETE FROM
  processed_declared_licenses_mapped_declared_licenses pdlmdl
WHERE pdlmdl.processed_declared_license_id IN (
  SELECT
    pdl.id
  FROM
    processed_declared_licenses pdl
  WHERE pdl.package_id NOT IN (
    SELECT
      d.id
    FROM
      deduplicated_packages d
  )
  AND pdl.package_id IS NOT NULL
);

DELETE FROM
  processed_declared_licenses_unmapped_declared_licenses pdlmdl
WHERE pdlmdl.processed_declared_license_id IN (
  SELECT
    pdl.id
  FROM
    processed_declared_licenses pdl
  WHERE pdl.package_id NOT IN (
    SELECT
      d.id
    FROM
      deduplicated_packages d
  )
  AND pdl.package_id IS NOT NULL
);

DELETE FROM
  processed_declared_licenses pdl
WHERE pdl.package_id NOT IN (
  SELECT
    d.id
  FROM
    deduplicated_packages d
)
AND pdl.package_id IS NOT NULL;

DELETE FROM
  packages p
WHERE p.id NOT IN (
  SELECT
    d.id
  FROM
    deduplicated_packages d
);

DELETE FROM
  projects_authors pa
WHERE pa.project_id NOT IN (
  SELECT
    d.id
  FROM
    deduplicated_projects d
);

DELETE FROM
  projects_declared_licenses pdl
WHERE pdl.project_id NOT IN (
  SELECT
    d.id
  FROM
    deduplicated_projects d
);

DELETE FROM
  project_scopes ps
WHERE ps.project_id NOT IN (
  SELECT
    d.id
  FROM
    deduplicated_projects d
);

DELETE FROM
  processed_declared_licenses_mapped_declared_licenses pdlmdl
WHERE pdlmdl.processed_declared_license_id IN (
  SELECT
    pdl.id
  FROM
    processed_declared_licenses pdl
  WHERE pdl.project_id NOT IN (
    SELECT
      d.id
    FROM
      deduplicated_projects d
  )
  AND pdl.project_id IS NOT NULL
);

DELETE FROM
  processed_declared_licenses_unmapped_declared_licenses pdlmdl
WHERE pdlmdl.processed_declared_license_id IN (
  SELECT
    pdl.id
  FROM
    processed_declared_licenses pdl
  WHERE pdl.project_id NOT IN (
    SELECT
      d.id
    FROM
      deduplicated_projects d
  )
  AND pdl.project_id IS NOT NULL
);

DELETE FROM
  processed_declared_licenses pdl
WHERE pdl.project_id NOT IN (
  SELECT
    d.id
  FROM
    deduplicated_projects d
)
AND pdl.project_id IS NOT NULL;

DELETE FROM
  projects p
WHERE p.id NOT IN (
  SELECT
    d.id
  FROM
    deduplicated_projects d
);

-- Step 4: Recreate link tables.

INSERT INTO
  packages_analyzer_runs
SELECT
  pwh.id,
  parh.analyzer_run_id
FROM
  packages_analyzer_runs_hashes parh
INNER JOIN packages_with_hashes pwh ON parh.hash = pwh.hash;

INSERT INTO
  projects_analyzer_runs
SELECT
  pwh.id,
  parh.analyzer_run_id
FROM
  projects_analyzer_runs_hashes parh
INNER JOIN projects_with_hashes pwh ON parh.hash = pwh.hash;

-- Cleanup.

DROP TABLE packages_analyzer_runs_hashes;

DROP TABLE deduplicated_packages;

DROP VIEW packages_with_hashes;

DROP VIEW packages_with_authors;

DROP VIEW packages_with_processed_licenses;

DROP VIEW packages_with_licenses;

DROP TABLE projects_analyzer_runs_hashes;

DROP TABLE deduplicated_projects;

DROP VIEW projects_with_hashes;

DROP VIEW projects_with_authors;

DROP VIEW projects_with_scopes;

DROP VIEW projects_with_processed_licenses;

DROP VIEW projects_with_licenses;

DROP VIEW processed_declared_licenses_with_hashes;

DROP VIEW processed_declared_licenses_with_unmapped_licenses;

DROP VIEW processed_declared_licenses_with_mapped_licenses;
