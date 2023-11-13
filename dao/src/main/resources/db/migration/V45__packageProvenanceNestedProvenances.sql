-- Drop unique constraint as multiple entries are now allowed.
ALTER TABLE nested_provenances
    DROP CONSTRAINT nested_provenances_root_vcs_id_root_resolved_revision_has_o_key;

-- Add index similar to previously removed constraint as it is still needed for querying.
CREATE INDEX package_provenances_vcs_id_resolved_revision_has_only_fixed_rev
    ON nested_provenances (root_vcs_id, root_resolved_revision, has_only_fixed_revisions);

-- Add column to associate package provenances with nested provenances.
ALTER TABLE package_provenances
    ADD COLUMN nested_provenance_id bigint REFERENCES nested_provenances NULL;
