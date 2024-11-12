-- This migration script modifies the foreign key constraints of the tables that reference ort_runs to add an
-- ON DELETE CASCADE clause. With this setup, it is very easy to delete an ORT run and all its associated data.

ALTER TABLE notifier_runs
  DROP CONSTRAINT notifier_runs_notifier_job_id_fkey,
  ADD CONSTRAINT notifier_runs_notifier_job_id_fkey
    FOREIGN KEY (notifier_job_id) REFERENCES notifier_jobs (id) ON DELETE CASCADE;

ALTER TABLE notifier_jobs
  DROP CONSTRAINT notifier_jobs_ort_run_id_fkey,
  ADD CONSTRAINT notifier_jobs_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;

ALTER TABLE packages_analyzer_runs
  DROP CONSTRAINT packages_analyzer_runs_analyzer_run_id_fkey,
  ADD CONSTRAINT packages_analyzer_runs_analyzer_run_id_fkey
    FOREIGN KEY (analyzer_run_id) REFERENCES analyzer_runs (id) ON DELETE CASCADE;

ALTER TABLE projects_analyzer_runs
  DROP CONSTRAINT projects_analyzer_runs_analyzer_run_id_fkey,
  ADD CONSTRAINT projects_analyzer_runs_analyzer_run_id_fkey
    FOREIGN KEY (analyzer_run_id) REFERENCES analyzer_runs (id) ON DELETE CASCADE;

ALTER TABLE analyzer_configurations_package_manager_configurations
  DROP CONSTRAINT analyzer_configurations_package__analyzer_configuration_id_fkey,
  ADD CONSTRAINT analyzer_configurations_package__analyzer_configuration_id_fkey
    FOREIGN KEY (analyzer_configuration_id) REFERENCES analyzer_configurations (id) ON DELETE CASCADE;

ALTER TABLE analyzer_configurations
  DROP CONSTRAINT analyzer_configurations_analyzer_run_id_fkey,
  ADD CONSTRAINT analyzer_configurations_analyzer_run_id_fkey
    FOREIGN KEY (analyzer_run_id) REFERENCES analyzer_runs (id) ON DELETE CASCADE;

ALTER TABLE analyzer_runs
  DROP CONSTRAINT analyzer_runs_analyzer_job_id_fkey,
  ADD CONSTRAINT analyzer_runs_analyzer_job_id_fkey
    FOREIGN KEY (analyzer_job_id) REFERENCES analyzer_jobs (id) ON DELETE CASCADE;

ALTER TABLE analyzer_jobs
  DROP CONSTRAINT analyzer_jobs_ort_run_id_fkey,
  ADD CONSTRAINT analyzer_jobs_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;

ALTER TABLE advisor_results_vulnerabilities
  DROP CONSTRAINT advisor_results_vulnerabilities_advisor_result_id_fkey,
  ADD CONSTRAINT advisor_results_vulnerabilities_advisor_result_id_fkey
    FOREIGN KEY (advisor_result_id) REFERENCES advisor_results (id) ON DELETE CASCADE;

ALTER TABLE advisor_results_defects
  DROP CONSTRAINT advisor_results_defects_advisor_result_id_fkey,
  ADD CONSTRAINT advisor_results_defects_advisor_result_id_fkey
    FOREIGN KEY (advisor_result_id) REFERENCES advisor_results (id) ON DELETE CASCADE;

ALTER TABLE advisor_results
  DROP CONSTRAINT advisor_results_advisor_run_identifier_id_fkey,
  ADD CONSTRAINT advisor_results_advisor_run_identifier_id_fkey
    FOREIGN KEY (advisor_run_identifier_id) REFERENCES advisor_runs_identifiers (id) ON DELETE CASCADE;

ALTER TABLE advisor_runs_identifiers
  DROP CONSTRAINT advisor_runs_identifiers_advisor_run_id_fkey,
  ADD CONSTRAINT advisor_runs_identifiers_advisor_run_id_fkey
    FOREIGN KEY (advisor_run_id) REFERENCES advisor_runs (id) ON DELETE CASCADE;

ALTER TABLE advisor_configurations_options
  DROP CONSTRAINT advisor_configurations_options_advisor_configuration_id_fkey,
  ADD CONSTRAINT advisor_configurations_options_advisor_configuration_id_fkey
    FOREIGN KEY (advisor_configuration_id) REFERENCES advisor_configurations (id) ON DELETE CASCADE;

ALTER TABLE advisor_configurations_secrets
  DROP CONSTRAINT advisor_configurations_secrets_advisor_configuration_id_fkey,
  ADD CONSTRAINT advisor_configurations_secrets_advisor_configuration_id_fkey
    FOREIGN KEY (advisor_configuration_id) REFERENCES advisor_configurations (id) ON DELETE CASCADE;

ALTER TABLE advisor_configurations
  DROP CONSTRAINT advisor_configurations_advisor_run_id_fkey,
  ADD CONSTRAINT advisor_configurations_advisor_run_id_fkey
    FOREIGN KEY (advisor_run_id) REFERENCES advisor_runs (id) ON DELETE CASCADE;

ALTER TABLE advisor_runs
  DROP CONSTRAINT advisor_runs_advisor_job_id_fkey,
  ADD CONSTRAINT advisor_runs_advisor_job_id_fkey
    FOREIGN KEY (advisor_job_id) REFERENCES advisor_jobs (id) ON DELETE CASCADE;

ALTER TABLE advisor_jobs
  DROP CONSTRAINT advisor_jobs_ort_run_id_fkey,
  ADD CONSTRAINT advisor_jobs_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;

ALTER TABLE scanner_runs_package_provenances
  DROP CONSTRAINT scanner_runs_package_provenances_scanner_run_id_fkey,
  ADD CONSTRAINT scanner_runs_package_provenances_scanner_run_id_fkey
    FOREIGN KEY (scanner_run_id) REFERENCES scanner_runs (id) ON DELETE CASCADE;

ALTER TABLE scanner_runs_scan_results
  DROP CONSTRAINT scanner_runs_scan_results_scanner_run_id_fkey,
  ADD CONSTRAINT scanner_runs_scan_results_scanner_run_id_fkey
    FOREIGN KEY (scanner_run_id) REFERENCES scanner_runs (id) ON DELETE CASCADE;

ALTER TABLE scanner_runs_scanners
  DROP CONSTRAINT scanner_runs_scanners_scanner_run_id_fkey,
  ADD CONSTRAINT scanner_runs_scanners_scanner_run_id_fkey
    FOREIGN KEY (scanner_run_id) REFERENCES scanner_runs (id) ON DELETE CASCADE;

ALTER TABLE scanner_configurations_detected_license_mappings
  DROP CONSTRAINT scanner_configurations_detected_l_scanner_configuration_id_fkey,
  ADD CONSTRAINT scanner_configurations_detected_l_scanner_configuration_id_fkey
    FOREIGN KEY (scanner_configuration_id) REFERENCES scanner_configurations (id) ON DELETE CASCADE;

ALTER TABLE scanner_configurations_options
  DROP CONSTRAINT scanner_configurations_options_scanner_configuration_id_fkey,
  ADD CONSTRAINT scanner_configurations_options_scanner_configuration_id_fkey
    FOREIGN KEY (scanner_configuration_id) REFERENCES scanner_configurations (id) ON DELETE CASCADE;

ALTER TABLE scanner_configurations_secrets
  DROP CONSTRAINT scanner_configurations_secrets_scanner_configuration_id_fkey,
  ADD CONSTRAINT scanner_configurations_secrets_scanner_configuration_id_fkey
    FOREIGN KEY (scanner_configuration_id) REFERENCES scanner_configurations (id) ON DELETE CASCADE;

ALTER TABLE scanner_configurations
  DROP CONSTRAINT scanner_configurations_scanner_run_id_fkey,
  ADD CONSTRAINT scanner_configurations_scanner_run_id_fkey
    FOREIGN KEY (scanner_run_id) REFERENCES scanner_runs (id) ON DELETE CASCADE;

ALTER TABLE scanner_runs
  DROP CONSTRAINT scanner_runs_scanner_job_id_fkey,
  ADD CONSTRAINT scanner_runs_scanner_job_id_fkey
    FOREIGN KEY (scanner_job_id) REFERENCES scanner_jobs (id) ON DELETE CASCADE;

ALTER TABLE scanner_jobs
  DROP CONSTRAINT scanner_jobs_ort_run_id_fkey,
  ADD CONSTRAINT scanner_jobs_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;

ALTER TABLE evaluator_runs_rule_violations
  DROP CONSTRAINT evaluator_runs_rule_violations_evaluator_run_id_fkey,
  ADD CONSTRAINT evaluator_runs_rule_violations_evaluator_run_id_fkey
    FOREIGN KEY (evaluator_run_id) REFERENCES evaluator_runs (id) ON DELETE CASCADE;

ALTER TABLE evaluator_runs
  DROP CONSTRAINT evaluator_runs_evaluator_job_id_fkey,
  ADD CONSTRAINT evaluator_runs_evaluator_job_id_fkey
    FOREIGN KEY (evaluator_job_id) REFERENCES evaluator_jobs (id) ON DELETE CASCADE;

ALTER TABLE evaluator_jobs
  DROP CONSTRAINT evaluator_jobs_ort_run_id_fkey,
  ADD CONSTRAINT evaluator_jobs_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;

ALTER TABLE reporter_runs_reports
  DROP CONSTRAINT reporter_runs_reports_reporter_run_id_fkey,
  ADD CONSTRAINT reporter_runs_reports_reporter_run_id_fkey
    FOREIGN KEY (reporter_run_id) REFERENCES reporter_runs (id) ON DELETE CASCADE;

ALTER TABLE reporter_runs
  DROP CONSTRAINT reporter_runs_reporter_job_id_fkey,
  ADD CONSTRAINT reporter_runs_reporter_job_id_fkey
    FOREIGN KEY (reporter_job_id) REFERENCES reporter_jobs (id) ON DELETE CASCADE;

ALTER TABLE reporter_jobs
  DROP CONSTRAINT reporter_jobs_ort_run_id_fkey,
  ADD CONSTRAINT reporter_jobs_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;

ALTER TABLE infrastructure_services_ort_runs
  DROP CONSTRAINT infrastructure_services_ort_runs_ort_run_id_fkey,
  ADD CONSTRAINT infrastructure_services_ort_runs_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;

ALTER TABLE ort_runs_labels
  DROP CONSTRAINT ort_runs_labels_ort_run_id_fkey,
  ADD CONSTRAINT ort_runs_labels_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;

ALTER TABLE ort_runs_issues
  DROP CONSTRAINT ort_runs_issues2_ort_run_id_fkey,
  ADD CONSTRAINT ort_runs_issues2_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations
  DROP CONSTRAINT repository_configurations_ort_run_id_fkey,
  ADD CONSTRAINT repository_configurations_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations_issue_resolutions
  DROP CONSTRAINT repository_configurations_issu_repository_configuration_id_fkey,
  ADD CONSTRAINT repository_configurations_issu_repository_configuration_id_fkey
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations_license_finding_curations
  DROP CONSTRAINT repository_configurations_lice_repository_configuration_id_fkey,
  ADD CONSTRAINT repository_configurations_lice_repository_configuration_id_fkey
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations_package_curations
  DROP CONSTRAINT repository_configurations_pack_repository_configuration_id_fkey,
  ADD CONSTRAINT repository_configurations_pack_repository_curation_id_fkey
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations_package_configurations
  DROP CONSTRAINT repository_configurations_pac_repository_configuration_id_fkey1,
  ADD CONSTRAINT repository_configurations_pac_repository_configuration_id_fkey
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations_package_license_choices
  DROP CONSTRAINT repository_configurations_pac_repository_configuration_id_fkey2,
  ADD CONSTRAINT repository_configurations_pac_license_choices_id_fkey
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations_path_excludes
  DROP CONSTRAINT repository_configurations_path_repository_configuration_id_fkey,
  ADD CONSTRAINT repository_configurations_path_repository_configuration_id_fkey
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations_provenance_snippet_choices
  DROP CONSTRAINT repository_configurations_prov_repository_configuration_id_fkey,
  ADD CONSTRAINT repository_configurations_prov_repository_configuration_id_fkey
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations_rule_violation_resolutions
  DROP CONSTRAINT repository_configurations_rule_repository_configuration_id_fkey,
  ADD CONSTRAINT repository_configurations_rule_repository_configuration_id_fkey
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations_scope_excludes
  DROP CONSTRAINT repository_configurations_scop_repository_configuration_id_fkey,
  ADD CONSTRAINT repository_configurations_scop_repository_configuration_id_fkey
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations_spdx_license_choices
  DROP CONSTRAINT repository_configurations_spdx_repository_configuration_id_fkey,
  ADD CONSTRAINT repository_configurations_spdx_repository_configuration_id_fkey
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;

ALTER TABLE repository_configurations_vulnerability_resolutions
  DROP CONSTRAINT repository_configurations_vuln_repository_configuration_id_fkey,
  ADD CONSTRAINT repository_configurations_vuln_repository_configuration_id_fkey
    FOREIGN KEY (repository_configuration_id) REFERENCES repository_configurations (id) ON DELETE CASCADE;

ALTER TABLE resolved_package_curations
  DROP CONSTRAINT resolved_package_curations_resolved_package_curation_provi_fkey,
  ADD CONSTRAINT resolved_package_curations_resolved_package_curation_provi_fkey
    FOREIGN KEY (resolved_package_curation_provider_id) REFERENCES resolved_package_curation_providers (id) ON DELETE CASCADE;

ALTER TABLE resolved_configurations_issue_resolutions
  DROP CONSTRAINT resolved_configurations_issue_re_resolved_configuration_id_fkey,
  ADD CONSTRAINT resolved_configurations_issue_re_resolved_configuration_id_fkey
    FOREIGN KEY (resolved_configuration_id) REFERENCES resolved_configurations (id) ON DELETE CASCADE;

ALTER TABLE resolved_configurations_rule_violation_resolutions
  DROP CONSTRAINT resolved_configurations_rule_vio_resolved_configuration_id_fkey,
  ADD CONSTRAINT resolved_configurations_rule_vio_resolved_configuration_id_fkey
    FOREIGN KEY (resolved_configuration_id) REFERENCES resolved_configurations (id) ON DELETE CASCADE;

ALTER TABLE resolved_configurations_vulnerability_resolutions
  DROP CONSTRAINT resolved_configurations_vulnerab_resolved_configuration_id_fkey,
  ADD CONSTRAINT resolved_configurations_vulnerab_resolved_configuration_id_fkey
    FOREIGN KEY (resolved_configuration_id) REFERENCES resolved_configurations (id) ON DELETE CASCADE;

ALTER TABLE resolved_configurations_package_configurations
  DROP CONSTRAINT resolved_configurations_package__resolved_configuration_id_fkey,
  ADD CONSTRAINT resolved_configurations_package__resolved_configuration_id_fkey
    FOREIGN KEY (resolved_configuration_id) REFERENCES resolved_configurations (id) ON DELETE CASCADE;

ALTER TABLE resolved_package_curations
  DROP CONSTRAINT resolved_package_curations_resolved_package_curation_provi_fkey,
  ADD CONSTRAINT resolved_package_curations_resolved_package_curation_provi_fkey
    FOREIGN KEY (resolved_package_curation_provider_id) REFERENCES resolved_package_curation_providers (id) ON DELETE CASCADE;

ALTER TABLE resolved_package_curation_providers
  DROP CONSTRAINT resolved_package_curation_provid_resolved_configuration_id_fkey,
  ADD CONSTRAINT resolved_package_curation_provid_resolved_configuration_id_fkey
    FOREIGN KEY (resolved_configuration_id) REFERENCES resolved_configurations (id) ON DELETE CASCADE;

ALTER TABLE resolved_configurations
  DROP CONSTRAINT resolved_configurations_ort_run_id_fkey,
  ADD CONSTRAINT resolved_configurations_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;

ALTER TABLE nested_repositories
  DROP CONSTRAINT nested_repositories_ort_run_id_fkey,
  ADD CONSTRAINT nested_repositories_ort_run_id_fkey
    FOREIGN KEY (ort_run_id) REFERENCES ort_runs (id) ON DELETE CASCADE;
