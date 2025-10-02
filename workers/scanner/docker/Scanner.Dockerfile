# syntax=docker/dockerfile:1.19

# This Dockerfile is the base image for the Scanner Docker image. It contains all the tooling required to run the
# Scanner.

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

FROM ${BASE_REGISTRY}ort-server-base-image:$BASE_IMAGE_TAG AS base-image

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    bzip2 \
    cmake \
    curl \
    gcc \
    git \
    libffi-dev \
    libgcrypt20 \
    libgmp-dev \
    libgomp1 \
    libpopt0 \
    libsqlite3-0 \
    libssl-dev \
    libxml2-dev \
    libxslt1-dev \
    libyaml-dev \
    libzstd1 \
    make \
    pkg-config \
    python3 \
    python3-pip \
    sudo \
    unzip \
    xz-utils \
    zlib1g \
    zlib1g-dev \
    && sudo rm -rf /var/lib/apt/lists/*

ARG ASKALONO_VERSION=0.5.0
ARG LICENSEE_VERSION=9.18.0
ARG RUBY_VERSION=3.4.4
ARG SCANCODE_VERSION=32.4.1

# Install Askalono
RUN curl -LOs https://github.com/amzn/askalono/releases/download/$ASKALONO_VERSION/askalono-Linux.zip && \
    mkdir /opt/askalono && \
    unzip askalono-Linux.zip -d /opt/askalono

ENV PATH=$PATH:/opt/askalono

# Use rbenv to install Licensee
ENV RBENV_ROOT=/opt/rbenv
ENV PATH=$RBENV_ROOT/bin:$RBENV_ROOT/shims/:$RBENV_ROOT/plugins/ruby-build/bin:$PATH

RUN git clone --depth 1 https://github.com/rbenv/rbenv.git $RBENV_ROOT
RUN git clone --depth 1 https://github.com/rbenv/ruby-build.git "$(rbenv root)"/plugins/ruby-build
WORKDIR $RBENV_ROOT

RUN src/configure \
    && make -C src

RUN rbenv install $RUBY_VERSION -v \
    && rbenv global $RUBY_VERSION \
    && gem install licensee:$LICENSEE_VERSION

# Use pip to install ScanCode
RUN curl -Os https://raw.githubusercontent.com/nexB/scancode-toolkit/v$SCANCODE_VERSION/requirements.txt && \
    pip install -U --constraint requirements.txt scancode-toolkit==$SCANCODE_VERSION && \
    rm requirements.txt

ENV PATH="/home/ort/.local/bin:${PATH}"
