#!/bin/bash

# This script is used in a docker compose setup to initialize default KeyCloak entities using OpenTofu.

set -eu

cd /srv/workspace
tofu init
tofu import keycloak_realm.realm master || true  # this only needs to be done once
tofu apply --auto-approve
