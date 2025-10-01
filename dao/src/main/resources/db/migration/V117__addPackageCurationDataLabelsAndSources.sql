-- This migration script adds a table for package curation data labels
-- and a column for source code origins to the package_curation_data table.

CREATE TABLE package_curation_data_labels
(
    id                          bigserial   PRIMARY KEY,
    package_curation_data_id    bigint      NOT NULL REFERENCES package_curation_data(id) ON DELETE CASCADE,
    key                         text        NOT NULL,
    value                       text        NOT NULL
);

ALTER TABLE package_curation_data
  ADD COLUMN source_code_origins text;
