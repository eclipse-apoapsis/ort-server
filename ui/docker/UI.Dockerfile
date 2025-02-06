# syntax=docker/dockerfile:1.13

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

# Stage 1: Build the UI.
FROM node:22-slim@sha256:d6d1b3a6f21a25e43d765816281b4a86e5f1ebf843cfae1b14dd0f1c28257cc7 AS build

ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable && corepack prepare pnpm@9.15.4 --activate

WORKDIR /app
COPY . .

ENV VITE_API_URL="UI_API_URL_PLACEHOLDER" \
    VITE_AUTHORITY="UI_AUTHORITY_PLACEHOLDER" \
    VITE_BASEPATH="UI_BASEPATH_PLACEHOLDER" \
    VITE_CLIENT_ID="UI_CLIENT_ID_PLACEHOLDER" \
    VITE_CLIENT_ID_SERVER="UI_CLIENT_ID_SERVER_PLACEHOLDER" \
    VITE_UI_URL="UI_URL_PLACEHOLDER"

RUN pnpm install --frozen-lockfile
RUN pnpm run build

# Stage 2: Serve the app with nginx.
FROM nginx:1.27-alpine@sha256:848dc68e75ce52eb703318c8121627a5aec1e24a85a1aad16545338fd3e7da4b

# Copy the build output to the nginx html directory.
COPY --from=build /app/dist /usr/share/nginx/html

# Copy custom nginx configuration.
COPY docker/nginx.conf.template /etc/nginx/conf.d/default.conf.template

# Copy entrypoint script.
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Make sure the user executing the container has access rights to the directories required by nginx.
RUN chgrp -R 0 /var/cache/nginx /var/run /var/log/nginx /usr/share/nginx/html /etc/nginx/conf.d \
    && chmod -R g+rwX /var/cache/nginx /var/run /var/log/nginx /usr/share/nginx/html /etc/nginx/conf.d

# Expose port 8080.
EXPOSE 8080

# Set the entrypoint script.
ENTRYPOINT ["/entrypoint.sh"]
