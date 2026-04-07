-- Add a name column to the repositories table.

ALTER TABLE repositories ADD COLUMN IF NOT EXISTS name TEXT;
ALTER TABLE repositories DROP CONSTRAINT repositories_url_product_id_key;
ALTER TABLE repositories
    ADD CONSTRAINT repositories_url_name_product_id_key UNIQUE NULLS NOT DISTINCT (url, name, product_id);
