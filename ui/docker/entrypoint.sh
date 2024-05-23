#!/bin/sh

# Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

# Set default value for VITE_UI_URL as the default value does not work for the Docker image which uses a different port.
: "${VITE_UI_URL:=http://localhost:8082/}"

# Set the default value for VITE_CLIENT_ID to use the Keycloak client with matching root and home URLs.
: "${VITE_CLIENT_ID:=ort-server-ui}"

# Replace placeholders with actual environment variables in JavaScript files.
find /usr/share/nginx/html/assets -name '*.js' -exec sed -i "s#VITE_UI_URL||\"http://localhost:5173/\"#VITE_UI_URL||\"$VITE_UI_URL\"#g" {} +

if [ -n "$VITE_API_URL" ]; then
  find /usr/share/nginx/html/assets -name '*.js' -exec sed -i "s#VITE_API_URL||\"http://localhost:8080\"#VITE_API_URL||\"$VITE_API_URL\"#g" {} +
fi

if [ -n "$VITE_AUTHORITY" ]; then
  find /usr/share/nginx/html/assets -name '*.js' -exec sed -i "s#VITE_AUTHORITY||\"http://localhost:8081/realms/master\"#VITE_AUTHORITY||\"$VITE_AUTHORITY\"#g" {} +
fi

if [ -n "$VITE_CLIENT_ID" ]; then
  find /usr/share/nginx/html/assets -name '*.js' -exec sed -i "s#VITE_CLIENT_ID||\"ort-server-ui-dev\"#VITE_CLIENT_ID||\"$VITE_CLIENT_ID\"#g" {} +
fi

# Start nginx.
exec nginx -g 'daemon off;'
