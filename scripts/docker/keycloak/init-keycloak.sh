#!/bin/bash

# This script is used in a docker compose setup to initialize default KeyCloak entities.

set -eu

KEYCLOAK_SCRIPT=/opt/keycloak/bin/kc.sh

if [[ $# -gt 1 ]]
then
  KEYCLOAK_SCRIPT=$1
  shift
fi

echo "Using Keycloak script: $KEYCLOAK_SCRIPT."

# Import the preconfigured realm.
# To add new entities, do the following:
# 1. Start the container.
# 2. Get a shell in the container using `docker exec -it ort-server-keycloak-1 /bin/bash`.
# 3. Configure the new entities in the admin UI: `http://localhost:8081/admin` (username: `admin`, password: `admin`).
# 4. Export the configuration:
#    `KC_HTTP_PORT=8081 /opt/keycloak/bin/kc.sh export --dir /opt/keycloak_init --users realm_file
$KEYCLOAK_SCRIPT build
$KEYCLOAK_SCRIPT import --file /opt/keycloak_init/master-realm.json --spi-connections-jpa--quarkus--migration-strategy=update

# Start KeyCloak
$KEYCLOAK_SCRIPT "$@"
