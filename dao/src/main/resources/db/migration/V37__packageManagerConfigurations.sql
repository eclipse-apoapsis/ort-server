CREATE TABLE analyzer_configurations_package_manager_configurations
(
    analyzer_configuration_id bigint REFERENCES analyzer_configurations NOT NULL,
    package_manager_configuration_id bigint REFERENCES package_manager_configurations NOT NULL,

    PRIMARY KEY (analyzer_configuration_id, package_manager_configuration_id)
);

ALTER TABLE package_manager_configurations
    DROP COLUMN analyzer_configuration_id;
