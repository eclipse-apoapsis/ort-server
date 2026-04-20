-- The lateral subquery in getPackagesWithDetectedLicenseForRun() filters packages by identifier_id
-- on every outer row; without this index that causes a sequential scan of the entire packages table.
CREATE INDEX packages_identifier_id ON packages (identifier_id);
