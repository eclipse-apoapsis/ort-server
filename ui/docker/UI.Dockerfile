# syntax=docker/dockerfile:1.23

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
FROM node:24-slim@sha256:b506e7321f176aae77317f99d67a24b272c1f09f1d10f1761f2773447d8da26c AS build

ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME:$PATH"
RUN corepack enable

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
FROM nginx:1.30-alpine@sha256:0272e4604ed93c1792f03695a033a6e8546840f86e0de20a884bb17d2c924883

# Copy the build output to a template directory. The entrypoint script will copy the files to /usr/share/nginx/html at
# startup, allowing the serving directory to be a writable volume mount.
COPY --from=build /app/dist /usr/share/nginx/html-template

# Copy custom nginx configuration to /etc/nginx/. The entrypoint script will copy the file to
# /etc/nginx/conf.d/default.conf at startup, allowing /etc/nginx/conf.d to be a writable volume mount.
COPY docker/nginx.conf.template /etc/nginx/default.conf.template

# Configure nginx to run as non-root user for OpenShift compatibility
RUN sed -i 's/user  nginx;//g' /etc/nginx/nginx.conf \
    && sed -i 's|/var/run/nginx.pid|/tmp/nginx.pid|g' /etc/nginx/nginx.conf

# Copy entrypoint script.
COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Make sure the user executing the container has access rights to the directories required by nginx.
# The template directory only needs to be readable by the group.
RUN chgrp -R 0 /usr/share/nginx/html-template \
    && chmod -R g+rX /usr/share/nginx/html-template

# nginx needs to write to these directories at runtime.
RUN chgrp -R 0 /var/cache/nginx /var/run /var/log/nginx /etc/nginx/conf.d \
    && chmod -R g+rwX /var/cache/nginx /var/run /var/log/nginx /etc/nginx/conf.d

# Make /usr/share/nginx/html writable for the group so the entrypoint can copy files there.
RUN mkdir -p /usr/share/nginx/html \
    && chgrp -R 0 /usr/share/nginx/html \
    && chmod -R g+rwX /usr/share/nginx/html

# Expose port 8080.
EXPOSE 8080

# Set the entrypoint script.
ENTRYPOINT ["/entrypoint.sh"]
