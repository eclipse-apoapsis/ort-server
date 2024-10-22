-- This migration adds an index to the issues table on all its value columns to speed up the lookup for issues
-- for deduplication. Unfortunately, a plain index does not work, since the "message" property is not restricted
-- in size and can therefore exceed the maximum page size. To work around this, an expression index is used that
-- is based on a hash value.

-- Install the pgcrypto extension to be able to use the digest function.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Make sure that functions from extensions installed in the "public" schema are available.
SET search_path = ${flyway:defaultSchema}, public;

-- Create the expression index.
CREATE UNIQUE INDEX "issues_all_value_columns"
ON "issues"
USING btree ("source", "severity", "affected_path", digest("message", 'sha256'::text));
