ALTER TABLE packages_curation_results
    DROP CONSTRAINT pk_packages_curation_results_data;
ALTER TABLE packages_curation_results
    ADD COLUMN id BIGSERIAL PRIMARY KEY;
