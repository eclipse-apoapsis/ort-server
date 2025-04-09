# syntax=docker/dockerfile:1.14

# This Dockerfile is the base image for the Reporter Docker image.

# Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

# Build-Stage for Python executing scancode-license-data to get the license texts in a directory
FROM python:3.11-slim AS scancode-license-data-build

# Keep in sync with Scanner.Dockerfile
ARG SCANCODE_VERSION=32.3.3

RUN apt-get update && apt-get install -y curl libgomp1 && rm -rf /var/lib/apt/lists/*

# Use pip to install ScanCode
RUN curl -Os https://raw.githubusercontent.com/nexB/scancode-toolkit/v$SCANCODE_VERSION/requirements.txt && \
    pip install --no-cache-dir -U --constraint requirements.txt scancode-toolkit==$SCANCODE_VERSION && \
    rm requirements.txt

# Extract ScanCode license data to directory.
RUN scancode-license-data --path /opt/scancode-license-data \
    && find /opt/scancode-license-data -type f -not -name "*.LICENSE" -exec rm -f {} + \
    && rm -rf /opt/scancode-license-data/static

# When updating this version make sure to keep it in sync with the other worker Dockerfiles and libs.version.toml.
FROM eclipse-temurin:21.0.4_7-jdk-jammy@sha256:0472478e22da0f66043fa6acd8cd30126592349f47937adafc2340794e5bf06a

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    git \
    mercurial \
    repo \
    && rm -rf /var/lib/apt/lists/*

ARG USERNAME=ort
ARG USER_ID=1000
ARG USER_GID=$USER_ID
ARG HOMEDIR=/home/ort
ENV HOME=$HOMEDIR
ENV USER=$USERNAME

# Non privileged user
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd \
    --uid $USER_ID \
    --gid $USER_GID \
    --shell /bin/bash \
    --home-dir $HOMEDIR \
    --create-home $USERNAME

# Make sure the user executing the container has access rights in the home directory.
RUN chgrp -R 0 /home/ort && chmod -R g+rwX /home/ort

USER $USERNAME
WORKDIR $HOMEDIR

COPY --from=scancode-license-data-build --chown=$USER:$USER /opt/scancode-license-data /opt/scancode-license-data

ENTRYPOINT ["/bin/bash"]
