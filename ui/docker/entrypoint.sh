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

# Set default values that work in the Docker Compose environment.
: "${UI_API_URL:=http://localhost:8080}"
: "${UI_AUTHORITY:=http://localhost:8081/realms/master}"
: "${UI_BASEPATH:=/}"
: "${UI_CLIENT_ID:=ort-server-ui}"
: "${UI_CLIENT_ID_SERVER:=ort-server}"
: "${UI_URL:=http://localhost:8082/}"

# Replace placeholders with actual environment variables in JavaScript files.
find /usr/share/nginx/html/assets -name '*.js' -exec sed -i "s#UI_API_URL_PLACEHOLDER#$UI_API_URL#g" {} +
find /usr/share/nginx/html/assets -name '*.js' -exec sed -i "s#UI_AUTHORITY_PLACEHOLDER#$UI_AUTHORITY#g" {} +
find /usr/share/nginx/html/assets -name '*.js' -exec sed -i "s#UI_BASEPATH_PLACEHOLDER#$UI_BASEPATH#g" {} +
find /usr/share/nginx/html/assets -name '*.js' -exec sed -i "s#UI_CLIENT_ID_PLACEHOLDER#$UI_CLIENT_ID#g" {} +
find /usr/share/nginx/html/assets -name '*.js' -exec sed -i "s#UI_CLIENT_ID_SERVER_PLACEHOLDER#$UI_CLIENT_ID_SERVER#g" {} +
find /usr/share/nginx/html/assets -name '*.js' -exec sed -i "s#UI_URL_PLACEHOLDER#$UI_URL#g" {} +

# Replace placeholders with actual environment variables in the nginx configuration.
export UI_BASEPATH
envsubst '${UI_BASEPATH}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf
rm /etc/nginx/conf.d/default.conf.template

# Add base path to assets references in index.html.
sed -i "s|/assets/|${UI_BASEPATH}assets/|g" /usr/share/nginx/html/index.html

# Start nginx.
exec nginx -g 'daemon off;'
