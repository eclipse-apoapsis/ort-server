-- The unique constraint created by V51 contains the primary key, so it can never be violated. Its index only slows
-- down inserts, and as it contains the unbounded `spdx_expression` column, inserting very long SPDX expressions could
-- fail because of the maximum size of index entries.

ALTER TABLE processed_declared_licenses
    DROP CONSTRAINT IF EXISTS processed_declared_licenses_id_package_id_spdx_expression_key;
