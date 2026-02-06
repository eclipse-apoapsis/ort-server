# syntax=docker/dockerfile:1.21

# Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
# Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

# This is a common base image for all ORT Server images requiring Java. It provides some base functionality like
# setting file permissions for the user, setting up certificates, etc. And, it defines the temurin base image.

ARG TEMURIN_VERSION=21.0.10_7-jdk-jammy@sha256:4862c71ac621704c22fcdce4b4e055beebbe1e84d8a894c50045f3bff321aedd

FROM eclipse-temurin:$TEMURIN_VERSION

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

# Base package set
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    git \
    openssl \
    sudo \
    unzip \
    wget \
    && rm -rf /var/lib/apt/lists/*

ARG USERNAME=ort
ARG USER_ID=1001
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


RUN mkdir -p $HOMEDIR/.cache \
    && chown $USER:$USER $HOMEDIR/.cache

# Make sure the user executing the container has access rights in the home directory and other relevant directories.
RUN chgrp -R 0 $HOMEDIR \
    && chmod -R g+rwX $HOMEDIR \
    && chgrp $USER /opt \
    && chmod g+wx /opt \
    && chgrp -R 0 /opt/java \
    && chmod -R g+wx /opt/java \
    && chgrp -R 0 /etc/ssl/certs \
    && chmod -R g+rwX /etc/ssl/certs \
    && chgrp -R 0 /usr/local/share/ca-certificates/ \
    && chmod -R g+rwX /usr/local/share/ca-certificates/

RUN echo $LANG > /etc/locale.gen \
    && locale-gen $LANG \
    && update-locale LANG=$LANG

# sudo support
RUN echo "$USERNAME ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

# Support for custom certificates at build time.
COPY scripts/*.sh /etc/scripts/

# Set this to a directory containing CRT-files for custom certificates that ORT and all build tools should know about.
ARG CRT_FILES="*.crt"
COPY "$CRT_FILES" /tmp/certificates/

RUN /etc/scripts/export_proxy_certificates.sh /tmp/certificates/ \
    &&  /etc/scripts/import_certificates.sh /tmp/certificates/ \
    && rm /etc/scripts/export_proxy_certificates.sh

USER $USER
WORKDIR $HOME

ENTRYPOINT [ "/bin/bash" ]
