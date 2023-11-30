ALTER TABLE rule_violations
    DROP CONSTRAINT rule_violations_rule_package_identifier_id_license_license__key;

ALTER TABLE ort_issues
    DROP CONSTRAINT ort_issues_timestamp_source_message_severity_key;

CREATE INDEX rule_violations_all_value_columns
    ON rule_violations (rule, package_identifier_id, license, license_source, severity);

CREATE INDEX vulnerabilities_all_value_columns
    ON vulnerabilities (external_id, summary);

CREATE INDEX defects_all_value_columns
    ON defects (external_id, url, title, state, severity, creation_time, modification_time, closing_time, fix_release_version, fix_release_url);
