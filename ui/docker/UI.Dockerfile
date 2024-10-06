# syntax=docker/dockerfile:1.10

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
FROM node:20-slim@sha256:967bab29ecde5d59a6dd781054bf9021eee8116068e1f5cb139750b6bc6a75e9 AS build

ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable

WORKDIR /app
COPY . .

ENV VITE_API_URL="UI_API_URL_PLACEHOLDER" \
    VITE_AUTHORITY="UI_AUTHORITY_PLACEHOLDER" \
    VITE_BASEPATH="UI_BASEPATH_PLACEHOLDER" \
    VITE_CLIENT_ID="UI_CLIENT_ID_PLACEHOLDER" \
    VITE_UI_URL="UI_URL_PLACEHOLDER"

RUN pnpm install --frozen-lockfile
RUN pnpm run build

# Stage 2: Serve the app with nginx.
FROM nginx:1.27-alpine@sha256:2140dad235c130ac861018a4e13a6bc8aea3a35f3a40e20c1b060d51a7efd250

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
