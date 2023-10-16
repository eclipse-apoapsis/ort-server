-- Remove the tables for the removed advisor specific configuration classes.
ALTER TABLE advisor_configurations
    DROP COLUMN github_defects_configuration_id,
    DROP COLUMN nexus_iq_configuration_id,
    DROP COLUMN osv_configuration_id,
    DROP COLUMN vulnerable_code_configuration_id;

DROP TABLE github_defects_configurations, nexus_iq_configurations, osv_configurations, vulnerable_code_configurations;

-- Remove the old tables for advisor and scanner options.
DROP TABLE advisor_configuration_options, scanner_configuration_options, scanner_configurations_scanner_options;

-- Add new tables for advisor and scanner options and secrets.
CREATE TABLE advisor_configuration_options
(
    id      bigserial PRIMARY KEY,
    advisor text NOT NULL,
    option  text NOT NULL,
    value   text NOT NULL,

    UNIQUE (advisor, option, value)
);

CREATE TABLE advisor_configurations_options
(
    advisor_configuration_id        bigint REFERENCES advisor_configurations        NOT NULL,
    advisor_configuration_option_id bigint REFERENCES advisor_configuration_options NOT NULL,

    PRIMARY KEY (advisor_configuration_id, advisor_configuration_option_id)
);

CREATE TABLE advisor_configuration_secrets
(
    id      bigserial PRIMARY KEY,
    advisor text NOT NULL,
    secret  text NOT NULL,
    value   text NOT NULL,

    UNIQUE (advisor, secret, value)
);

CREATE TABLE advisor_configurations_secrets
(
    advisor_configuration_id        bigint REFERENCES advisor_configurations        NOT NULL,
    advisor_configuration_secret_id bigint REFERENCES advisor_configuration_secrets NOT NULL,

    PRIMARY KEY (advisor_configuration_id, advisor_configuration_secret_id)
);

CREATE TABLE scanner_configuration_options
(
    id      bigserial PRIMARY KEY,
    scanner text NOT NULL,
    option  text NOT NULL,
    value   text NOT NULL,

    UNIQUE (scanner, option, value)
);

CREATE TABLE scanner_configurations_options
(
    scanner_configuration_id        bigint REFERENCES scanner_configurations        NOT NULL,
    scanner_configuration_option_id bigint REFERENCES scanner_configuration_options NOT NULL,

    PRIMARY KEY (scanner_configuration_id, scanner_configuration_option_id)
);

CREATE TABLE scanner_configuration_secrets
(
    id      bigserial PRIMARY KEY,
    scanner text NOT NULL,
    secret  text NOT NULL,
    value   text NOT NULL,

    UNIQUE (scanner, secret, value)
);

CREATE TABLE scanner_configurations_secrets
(
    scanner_configuration_id        bigint REFERENCES scanner_configurations        NOT NULL,
    scanner_configuration_secret_id bigint REFERENCES scanner_configuration_secrets NOT NULL,

    PRIMARY KEY (scanner_configuration_id, scanner_configuration_secret_id)
);
