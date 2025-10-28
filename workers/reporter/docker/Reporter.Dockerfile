# syntax=docker/dockerfile:1.21

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

ARG BASE_REGISTRY=""
ARG BASE_IMAGE_TAG="latest"

# Build-Stage for Python executing scancode-license-data to get the license texts in a directory
FROM --platform=linux/amd64 python:3.13-slim AS scancode-license-data-build

# Keep in sync with Scanner.Dockerfile
ARG SCANCODE_VERSION=32.5.0

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    curl \
    libgomp1 \
    libxml2-dev \
    libxslt1-dev \
    && rm -rf /var/lib/apt/lists/*

# Use pip to install ScanCode
RUN curl -Os https://raw.githubusercontent.com/nexB/scancode-toolkit/v$SCANCODE_VERSION/requirements.txt && \
    pip install -U --constraint requirements.txt scancode-toolkit==$SCANCODE_VERSION setuptools && \
    rm requirements.txt

# Extract ScanCode license data to directory.
RUN scancode-license-data --path /opt/scancode-license-data \
    && find /opt/scancode-license-data -type f -not -name "*.LICENSE" -exec rm -f {} + \
    && rm -rf /opt/scancode-license-data/static

FROM ${BASE_REGISTRY}ort-server-base-image:$BASE_IMAGE_TAG AS base-image

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    git \
    mercurial \
    repo \
    && sudo rm -rf /var/lib/apt/lists/*

COPY --from=scancode-license-data-build --chown=$USER:$USER /opt/scancode-license-data /opt/scancode-license-data
