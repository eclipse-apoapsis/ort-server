ALTER TABLE reports
  ADD COLUMN download_link text DEFAULT '' NOT NULL,
  ADD COLUMN download_token_expiry_date TIMESTAMP DEFAULT '1970-01-01 00:00:00' NOT NULL;
