-- Create indexes to speed up finding the processed declared licenses for packages and projects.
CREATE INDEX processed_declared_licenses_package_id ON processed_declared_licenses (package_id);
CREATE INDEX processed_declared_licenses_project_id ON processed_declared_licenses (project_id);
