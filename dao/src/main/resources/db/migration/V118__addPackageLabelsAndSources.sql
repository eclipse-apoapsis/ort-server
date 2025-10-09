-- This migration script adds a table for package labels
-- and a column for source code origins to the packages table.

CREATE TABLE package_labels
(
    id            bigserial   PRIMARY KEY,
    package_id    bigint      NOT NULL REFERENCES packages(id) ON DELETE CASCADE,
    key           text        NOT NULL,
    value         text        NOT NULL
);

ALTER TABLE packages
  ADD COLUMN source_code_origins text DEFAULT NULL;
