#!/bin/bash

# Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# License-Filename: LICENSE

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
$KEYCLOAK_SCRIPT import --file /opt/keycloak_init/master-realm.json

# Start KeyCloak
$KEYCLOAK_SCRIPT "$@"
