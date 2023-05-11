CREATE INDEX organizations_name ON organizations (name);

CREATE INDEX products_name ON products (name);

CREATE INDEX repositories_type ON repositories (type);
CREATE INDEX repositories_url ON repositories (url);

CREATE INDEX secrets_name ON secrets (name);

CREATE INDEX ort_runs_revision ON ort_runs (revision);
CREATE INDEX ort_runs_created_at ON ort_runs (created_at);
