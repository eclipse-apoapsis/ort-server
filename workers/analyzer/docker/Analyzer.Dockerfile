# syntax=docker/dockerfile:1.17

# This Dockerfile is the base image for the Analyzer Docker image. It contains all the tooling required to run the
# Analyzer.

# Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
# Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

ARG ANDROID_CMD_VERSION=11076708
ARG BAZELISK_VERSION=1.20.0
ARG BOWER_VERSION=1.8.14
ARG COCOAPODS_VERSION=1.16.2
ARG COMPOSER_VERSION=2.8.4
ARG CONAN_VERSION=1.66.0
ARG CONAN2_VERSION=2.14.0
ARG DART_VERSION=2.18.4
ARG DOTNET_VERSION=6.0
ARG GO_VERSION=1.24.2
ARG HASKELL_STACK_VERSION=2.13.1
ARG NODEJS_VERSION=22.13.0
ARG NPM_VERSION=10.9.2
ARG NUGET_INSPECTOR_VERSION=0.9.12
ARG PIPTOOL_VERSION=25.0.1
ARG PYENV_GIT_TAG=v2.4.13
ARG PYTHON_INSPECTOR_VERSION=0.14.3
ARG PYTHON_PIPENV_VERSION=2023.12.1
ARG PYTHON_POETRY_PLUGIN_EXPORT_VERSION=1.9.0
ARG PYTHON_POETRY_VERSION=2.0.1
ARG PYTHON_SETUPTOOLS_VERSION=74.1.3
ARG PYTHON_VERSION=3.11.10
ARG RUBY_VERSION=3.4.4
ARG RUST_VERSION=1.84.0
ARG SBT_VERSION=1.10.0
ARG SWIFT_VERSION=6.0.3

# When updating this version make sure to keep it in sync with the other worker Dockerfiles and libs.version.toml.
ARG TEMURIN_VERSION=21.0.7_6-jdk-jammy@sha256:746ad7128069fdaa77df1f06a0463ad50f4ae787648cbbcc6d6ab0e702e6c97e

FROM eclipse-temurin:$TEMURIN_VERSION AS ort-base-image

ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

# Check and set apt proxy
COPY scripts/set_apt_proxy.sh /etc/scripts/set_apt_proxy.sh
RUN /etc/scripts/set_apt_proxy.sh

# Base package set
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ca-certificates \
    coreutils \
    curl \
    dirmngr \
    file \
    gcc \
    git \
    git-lfs \
    g++ \
    gnupg2 \
    iproute2 \
    libarchive-tools \
    libffi-dev \
    libgmp-dev \
    libmagic1 \
    libyaml-dev \
    libz-dev \
    locales \
    lzma \
    make \
    netbase \
    openssh-client \
    openssl \
    procps \
    rsync \
    sudo \
    tzdata \
    uuid-dev \
    unzip \
    wget \
    xz-utils \
    && rm -rf /var/lib/apt/lists/* \
    && git lfs install

RUN echo $LANG > /etc/locale.gen \
    && locale-gen $LANG \
    && update-locale LANG=$LANG

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

RUN chgrp $USER /opt \
    && chmod g+wx /opt

# sudo support
RUN echo "$USERNAME ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

# Copy certificates scripts only.
COPY scripts/*_certificates.sh /etc/scripts/

# Set this to a directory containing CRT-files for custom certificates that ORT and all build tools should know about.
ARG CRT_FILES="*.crt"
COPY "$CRT_FILES" /tmp/certificates/

RUN /etc/scripts/export_proxy_certificates.sh /tmp/certificates/ \
    &&  /etc/scripts/import_certificates.sh /tmp/certificates/

USER $USER
WORKDIR $HOME

ENTRYPOINT [ "/bin/bash" ]

#------------------------------------------------------------------------
# PYTHON - Build Python as a separate component with pyenv
FROM ort-base-image AS pythonbuild

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    libreadline-dev \
    libgdbm-dev \
    libsqlite3-dev \
    libssl-dev \
    libbz2-dev \
    liblzma-dev \
    tk-dev \
    && sudo rm -rf /var/lib/apt/lists/*

ARG PYTHON_VERSION
ARG PYENV_GIT_TAG

ENV PYENV_ROOT=/opt/python
ENV PATH=$PATH:$PYENV_ROOT/shims:$PYENV_ROOT/bin:$PYENV_ROOT/conan2/bin
RUN curl -kSs https://pyenv.run | bash \
    && pyenv install -v $PYTHON_VERSION \
    && pyenv global $PYTHON_VERSION

ARG CONAN_VERSION
ARG CONAN2_VERSION
ARG PYTHON_INSPECTOR_VERSION
ARG PYTHON_PIPENV_VERSION
ARG PYTHON_POETRY_PLUGIN_EXPORT_VERSION
ARG PYTHON_POETRY_VERSION
ARG PYTHON_SETUPTOOLS_VERSION
ARG PIPTOOL_VERSION

RUN pip install --no-cache-dir -U \
    pip=="$PIPTOOL_VERSION" \
    wheel \
    && pip install --no-cache-dir -U \
    Mercurial \
    conan=="$CONAN_VERSION" \
    pipenv=="$PYTHON_PIPENV_VERSION" \
    poetry=="$PYTHON_POETRY_VERSION" \
    poetry-plugin-export=="$PYTHON_POETRY_PLUGIN_EXPORT_VERSION" \
    python-inspector=="$PYTHON_INSPECTOR_VERSION" \
    setuptools=="$PYTHON_SETUPTOOLS_VERSION"
RUN mkdir /tmp/conan2 && cd /tmp/conan2 \
    && wget https://github.com/conan-io/conan/releases/download/$CONAN2_VERSION/conan-$CONAN2_VERSION-linux-x86_64.tgz \
    && tar -xvf conan-$CONAN2_VERSION-linux-x86_64.tgz\
    # Rename the Conan 2 executable to "conan2" to be able to call both Conan version from the package manager.
    && mkdir $PYENV_ROOT/conan2 && mv /tmp/conan2/bin $PYENV_ROOT/conan2/ \
    && mv $PYENV_ROOT/conan2/bin/conan $PYENV_ROOT/conan2/bin/conan2

FROM scratch AS python
COPY --from=pythonbuild /opt/python /opt/python

#------------------------------------------------------------------------
# NODEJS - Build NodeJS as a separate component with nvm
FROM ort-base-image AS nodebuild

ARG BOWER_VERSION
ARG NODEJS_VERSION
ARG NPM_VERSION

ENV NVM_DIR=/opt/nvm
ENV PATH=$PATH:$NVM_DIR/versions/node/v$NODEJS_VERSION/bin

RUN git clone --depth 1 https://github.com/nvm-sh/nvm.git $NVM_DIR
RUN . $NVM_DIR/nvm.sh \
    && nvm install "$NODEJS_VERSION" \
    && nvm alias default "$NODEJS_VERSION" \
    && nvm use default \
    && npm install --global npm@$NPM_VERSION bower@$BOWER_VERSION corepack@latest \
    && corepack enable

FROM scratch AS node
COPY --from=nodebuild /opt/nvm /opt/nvm

#------------------------------------------------------------------------
# RUBY - Build Ruby as a separate component with rbenv
FROM ort-base-image AS rubybuild

# hadolint ignore=DL3004
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    libreadline6-dev \
    libssl-dev \
    libz-dev \
    make \
    xvfb \
    zlib1g-dev \
    && sudo rm -rf /var/lib/apt/lists/*

ARG COCOAPODS_VERSION
ARG RUBY_VERSION
ENV RBENV_ROOT=/opt/rbenv
ENV PATH=$RBENV_ROOT/bin:$RBENV_ROOT/shims/:$RBENV_ROOT/plugins/ruby-build/bin:$PATH

RUN git clone --depth 1 https://github.com/rbenv/rbenv.git $RBENV_ROOT
RUN git clone --depth 1 https://github.com/rbenv/ruby-build.git "$(rbenv root)"/plugins/ruby-build
WORKDIR $RBENV_ROOT
RUN src/configure \
    && make -C src
RUN rbenv install $RUBY_VERSION -v \
    && rbenv global $RUBY_VERSION \
    && gem install bundler cocoapods:$COCOAPODS_VERSION

FROM scratch AS ruby
COPY --from=rubybuild /opt/rbenv /opt/rbenv

#------------------------------------------------------------------------
# RUST - Build as a separate component
FROM ort-base-image AS rustbuild

ARG RUST_HOME=/opt/rust
ARG CARGO_HOME=$RUST_HOME/cargo
ARG RUSTUP_HOME=$RUST_HOME/rustup
ARG RUST_VERSION
RUN curl -ksSf https://sh.rustup.rs | sh -s -- -y --profile minimal --default-toolchain $RUST_VERSION

FROM scratch AS rust
COPY --from=rustbuild /opt/rust /opt/rust

#------------------------------------------------------------------------
# GOLANG - Build as a separate component
FROM ort-base-image AS gobuild

ARG GO_VERSION
ENV GOBIN=/opt/go/bin
ENV PATH=$PATH:/opt/go/bin

SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN arch=$(arch | sed s/aarch64/arm64/ | sed s/x86_64/amd64/) \
    && curl -L https://dl.google.com/go/go${GO_VERSION}.linux-${arch}.tar.gz | tar -C /opt -xz

FROM scratch AS golang
COPY --from=gobuild /opt/go /opt/go

#------------------------------------------------------------------------
# HASKELL STACK
FROM ort-base-image AS haskellbuild

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    zlib1g-dev \
    && sudo rm -rf /var/lib/apt/lists/*

ARG HASKELL_STACK_VERSION

ENV HASKELL_HOME=/opt/haskell
ENV PATH=$PATH:$HASKELL_HOME/bin

RUN curl -sSL https://get.haskellstack.org/ | bash -s -- -d $HASKELL_HOME/bin

FROM scratch AS haskell
COPY --from=haskellbuild /opt/haskell /opt/haskell

#------------------------------------------------------------------------
# REPO / ANDROID SDK
FROM ort-base-image AS androidbuild

RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update -qq \
    && DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
    unzip \
    && sudo rm -rf /var/lib/apt/lists/*

ARG ANDROID_CMD_VERSION
ENV ANDROID_HOME=/opt/android-sdk

RUN --mount=type=tmpfs,target=/android \
    cd /android \
    && curl -Os https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip \
    && unzip -q commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip -d $ANDROID_HOME \
    && PROXY_HOST_AND_PORT=${https_proxy#*://} \
    && PROXY_HOST_AND_PORT=${PROXY_HOST_AND_PORT%/} \
    && if [ -n "$PROXY_HOST_AND_PORT" ]; then \
        # While sdkmanager uses HTTPS by default, the proxy type is still called "http".
        SDK_MANAGER_PROXY_OPTIONS="--proxy=http --proxy_host=${PROXY_HOST_AND_PORT%:*} --proxy_port=${PROXY_HOST_AND_PORT##*:}"; \
    fi \
    && yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager $SDK_MANAGER_PROXY_OPTIONS --sdk_root=$ANDROID_HOME "platform-tools" "cmdline-tools;latest"

RUN curl -ksS https://storage.googleapis.com/git-repo-downloads/repo | tee $ANDROID_HOME/cmdline-tools/bin/repo > /dev/null 2>&1 \
    && sudo chmod a+x $ANDROID_HOME/cmdline-tools/bin/repo

FROM scratch AS android
COPY --from=androidbuild /opt/android-sdk /opt/android-sdk

#------------------------------------------------------------------------
#  Dart
FROM ort-base-image AS dartbuild

ARG DART_VERSION
WORKDIR /opt/

ENV DART_SDK=/opt/dart-sdk
ENV PATH=$PATH:$DART_SDK/bin

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN --mount=type=tmpfs,target=/dart \
    arch=$(arch | sed s/aarch64/arm64/ | sed s/x86_64/x64/) \
    && curl -o /dart/dart.zip -L https://storage.googleapis.com/dart-archive/channels/stable/release/${DART_VERSION}/sdk/dartsdk-linux-${arch}-release.zip \
    && unzip /dart/dart.zip

FROM scratch AS dart
COPY --from=dartbuild /opt/dart-sdk /opt/dart-sdk

#------------------------------------------------------------------------
# SBT
FROM ort-base-image AS sbtbuild

ARG SBT_VERSION

ENV SBT_HOME=/opt/sbt
ENV PATH=$PATH:$SBT_HOME/bin

RUN curl -L https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz | tar -C /opt -xz

FROM scratch AS sbt
COPY --from=sbtbuild /opt/sbt /opt/sbt

#------------------------------------------------------------------------
# SWIFT
FROM ort-base-image AS swiftbuild

ARG SWIFT_VERSION

ENV SWIFT_HOME=/opt/swift
ENV PATH=$PATH:$SWIFT_HOME/bin

RUN mkdir -p $SWIFT_HOME \
    && echo $SWIFT_VERSION \
    && if [ "$(arch)" = "aarch64" ]; then \
    SWIFT_PACKAGE="ubuntu2204-aarch64/swift-$SWIFT_VERSION-RELEASE/swift-$SWIFT_VERSION-RELEASE-ubuntu22.04-aarch64.tar.gz"; \
    else \
    SWIFT_PACKAGE="ubuntu2204/swift-$SWIFT_VERSION-RELEASE/swift-$SWIFT_VERSION-RELEASE-ubuntu22.04.tar.gz"; \
    fi \
    && curl -L https://download.swift.org/swift-$SWIFT_VERSION-release/$SWIFT_PACKAGE \
    | tar -xz -C $SWIFT_HOME --strip-components=2

FROM scratch AS swift
COPY --from=swiftbuild /opt/swift /opt/swift

#------------------------------------------------------------------------
# DOTNET
FROM ort-base-image AS dotnetbuild

ARG DOTNET_VERSION
ARG NUGET_INSPECTOR_VERSION

ENV DOTNET_HOME=/opt/dotnet
ENV NUGET_INSPECTOR_HOME=$DOTNET_HOME
ENV PATH=$PATH:$DOTNET_HOME:$DOTNET_HOME/tools:$DOTNET_HOME/bin

# Note: We are not installing a dotnet package directly because
# debian packages from Ubuntu and Microsoft are incomplete

RUN mkdir -p $DOTNET_HOME \
    && echo $SWIFT_VERSION \
    && if [ "$(arch)" = "aarch64" ]; then \
    curl -L https://aka.ms/dotnet/$DOTNET_VERSION/dotnet-sdk-linux-arm64.tar.gz | tar -C $DOTNET_HOME -xz; \
    else \
    curl -L https://aka.ms/dotnet/$DOTNET_VERSION/dotnet-sdk-linux-x64.tar.gz | tar -C $DOTNET_HOME -xz; \
    fi

RUN mkdir -p $DOTNET_HOME/bin \
    && curl -L https://github.com/nexB/nuget-inspector/releases/download/v$NUGET_INSPECTOR_VERSION/nuget-inspector-v$NUGET_INSPECTOR_VERSION-linux-x64.tar.gz \
    | tar --strip-components=1 -C $DOTNET_HOME/bin -xz

FROM scratch AS dotnet
COPY --from=dotnetbuild /opt/dotnet /opt/dotnet

#------------------------------------------------------------------------
# BAZEL
FROM ort-base-image AS bazelbuild

ARG BAZELISK_VERSION

ENV BAZEL_HOME=/opt/bazel
ENV GOBIN=/opt/go/bin

RUN mkdir -p $BAZEL_HOME/bin \
    && if [ "$(arch)" = "aarch64" ]; then \
    curl -L https://github.com/bazelbuild/bazelisk/releases/download/v$BAZELISK_VERSION/bazelisk-linux-arm64 -o $BAZEL_HOME/bin/bazel; \
    else \
    curl -L https://github.com/bazelbuild/bazelisk/releases/download/v$BAZELISK_VERSION/bazelisk-linux-amd64 -o $BAZEL_HOME/bin/bazel; \
    fi \
    && chmod a+x $BAZEL_HOME/bin/bazel

COPY --from=gobuild /opt/go /opt/go

RUN $GOBIN/go install github.com/bazelbuild/buildtools/buildozer@latest && chmod a+x $GOBIN/buildozer

FROM scratch AS bazel
COPY --from=bazelbuild /opt/bazel /opt/bazel
COPY --from=bazelbuild /opt/go/bin/buildozer /opt/go/bin/buildozer

#------------------------------------------------------------------------
# Components container
FROM ort-base-image AS components

# Remove ort build scripts
RUN [ -d /etc/scripts ] && sudo rm -rf /etc/scripts

# Apt install commands.
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    sudo apt-get update && \
    DEBIAN_FRONTEND=noninteractive sudo apt-get install -y --no-install-recommends \
        php \
        subversion \
    && sudo rm -rf /var/lib/apt/lists/*

# Python
ENV PYENV_ROOT=/opt/python
ENV PATH=$PATH:$PYENV_ROOT/shims:$PYENV_ROOT/bin:$PYENV_ROOT/conan2/bin
COPY --from=python --chown=$USER:$USER $PYENV_ROOT $PYENV_ROOT

# NodeJS
ARG NODEJS_VERSION
ENV NVM_DIR=/opt/nvm
ENV PATH=$PATH:$NVM_DIR/versions/node/v$NODEJS_VERSION/bin
COPY --from=node --chown=$USER:$USER $NVM_DIR $NVM_DIR
# Required for Corepack to dynamically modify binaries of supported package managers.
RUN chmod -R g+rwX $NVM_DIR

# Rust
ENV RUST_HOME=/opt/rust
ENV CARGO_HOME=$RUST_HOME/cargo
ENV RUSTUP_HOME=$RUST_HOME/rustup
ENV PATH=$PATH:$CARGO_HOME/bin:$RUSTUP_HOME/bin
COPY --from=rust --chown=$USER:$USER /opt/rust /opt/rust
RUN chmod o+rwx $CARGO_HOME

# Golang
ENV PATH=$PATH:/opt/go/bin
COPY --from=golang --chown=$USER:$USER /opt/go /opt/go

# Ruby
ENV RBENV_ROOT=/opt/rbenv/
ENV GEM_HOME=/var/tmp/gem
ENV PATH=$PATH:$RBENV_ROOT/bin:$RBENV_ROOT/shims:$RBENV_ROOT/plugins/ruby-install/bin
COPY --from=ruby --chown=$USER:$USER $RBENV_ROOT $RBENV_ROOT

# Repo and Android
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_USER_HOME=$HOME/.android
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/cmdline-tools/bin
ENV PATH=$PATH:$ANDROID_HOME/platform-tools
COPY --from=android --chown=$USER:$USER $ANDROID_HOME $ANDROID_HOME
RUN chmod -R o+rw $ANDROID_HOME

# Swift
ENV SWIFT_HOME=/opt/swift
ENV PATH=$PATH:$SWIFT_HOME/bin
COPY --from=swift --chown=$USER:$USER $SWIFT_HOME $SWIFT_HOME

# SBT
ENV SBT_HOME=/opt/sbt
ENV PATH=$PATH:$SBT_HOME/bin
COPY --from=sbt --chown=$USER:$USER $SBT_HOME $SBT_HOME

# Dart
ENV DART_SDK=/opt/dart-sdk
ENV PATH=$PATH:$DART_SDK/bin
COPY --from=dart --chown=$USER:$USER $DART_SDK $DART_SDK

# Dotnet
ENV DOTNET_HOME=/opt/dotnet
ENV NUGET_INSPECTOR_HOME=$DOTNET_HOME
ENV PATH=$PATH:$DOTNET_HOME:$DOTNET_HOME/tools:$DOTNET_HOME/bin

COPY --from=dotnet --chown=$USER:$USER $DOTNET_HOME $DOTNET_HOME

# PHP composer
ARG COMPOSER_VERSION

ENV PATH=$PATH:/opt/php/bin
RUN mkdir -p /opt/php/bin \
    && curl -ksS https://getcomposer.org/installer | php -- --install-dir=/opt/php/bin --filename=composer --$COMPOSER_VERSION

# Haskell
ENV HASKELL_HOME=/opt/haskell
ENV PATH=$PATH:$HASKELL_HOME/bin
COPY --from=haskell --chown=$USER:$USER $HASKELL_HOME $HASKELL_HOME

# Bazel
ENV BAZEL_HOME=/opt/bazel
ENV PATH=$PATH:$BAZEL_HOME/bin

COPY --from=bazel $BAZEL_HOME $BAZEL_HOME
COPY --from=bazel --chown=$USER:$USER /opt/go/bin/buildozer /opt/go/bin/buildozer

# Make sure the user executing the container has access rights in the home directory.
RUN sudo chgrp -R 0 /home/ort && sudo chmod -R g+rwX /home/ort

USER $USERNAME
WORKDIR $HOMEDIR

ENTRYPOINT ["/bin/bash"]
