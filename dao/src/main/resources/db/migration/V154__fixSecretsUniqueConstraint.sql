-- The unique constraint on the secret name per hierarchy element never applied, because two of the referenced
-- columns are always NULL and NULL values are distinct by default. Recreate it so that NULL values are treated as
-- equal.

ALTER TABLE secrets DROP CONSTRAINT IF EXISTS secrets_name_organization_id_product_id_repository_id_key;
ALTER TABLE secrets
    ADD CONSTRAINT secrets_name_organization_id_product_id_repository_id_key
        UNIQUE NULLS NOT DISTINCT (name, organization_id, product_id, repository_id);
