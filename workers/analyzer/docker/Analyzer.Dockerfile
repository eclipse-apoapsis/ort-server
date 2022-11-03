# This Dockerfile is the base image for the Analyzer Docker image. It contains all the tooling required to run the
# Analyzer.

# syntax=docker/dockerfile:1.3

# Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

# Use OpenJDK Eclipe Temurin Ubuntu LTS
FROM eclipse-temurin:11-jdk-jammy as base-image

# Base package set
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ca-certificates \
    coreutils \
    cvs \
    curl \
    dirmngr \
    gcc \
    git \
    gnupg2 \
    iproute2 \
    libarchive-tools \
    libz-dev \
    locales \
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
    && rm -rf /var/lib/apt/lists/*

ARG USERNAME=ort
ARG USER_ID=1000
ARG USER_GID=$USER_ID
ARG HOMEDIR=/home/ort
ENV HOME=$HOMEDIR

# Non privileged user
RUN groupadd --gid $USER_GID $USERNAME \
    && useradd \
    --uid $USER_ID \
    --gid $USER_GID \
    --shell /bin/bash \
    --home-dir $HOMEDIR \
    --create-home $USERNAME

# sudo support
RUN echo "$USERNAME ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
    && chmod 0440 /etc/sudoers.d/$USERNAME

COPY scripts/add_local_path.sh /etc/profile.d/

#------------------------------------------------------------------------
FROM base-image AS build

#------------------------------------------------------------------------
# Ubuntu build toolchain
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        build-essential \
        dpkg-dev \
        libbluetooth-dev \
        libbz2-dev \
        libc6-dev \
        libexpat1-dev \
        libffi-dev \
        libgmp-dev \
        libgdbm-dev \
        liblzma-dev \
        libmpdec-dev \
        libncursesw5-dev \
        libreadline-dev \
        libsqlite3-dev \
        libssl-dev \
        make \
        tk-dev \
        zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

#------------------------------------------------------------------------
# PYTHON - Build Python as a separate component with pyenv
FROM build as pythonbuild

ARG PYTHON_VERSION=3.10.6
ARG PYENV_GIT_TAG=v2.3.4

ENV PYENV_ROOT=/opt/python
RUN curl -kSs https://pyenv.run | bash
ENV PATH=${PYENV_ROOT}/shims:${PYENV_ROOT}/bin:$PATH
RUN pyenv install -v ${PYTHON_VERSION}
RUN pyenv global ${PYTHON_VERSION}

COPY scripts/python.sh /etc/profile.d

ARG CONAN_VERSION=1.52.0
ARG PYTHON_INSPECTOR_VERSION=0.8.2
ARG PYTHON_PIPENV_VERSION=2018.11.26
ARG PYTHON_POETRY_VERSION=1.1.13
ARG PIPTOOL_VERSION=22.2.2
ENV PYENV_ROOT=/opt/python
ENV PATH=${PYENV_ROOT}/shims:${PYENV_ROOT}/bin:$PATH

RUN pip install -U \
    pip=="${PIPTOOL_VERSION}" \
    wheel \
    && pip install -U \
    Mercurial \
    conan=="${CONAN_VERSION}" \
    pipenv=="${PYTHON_PIPENV_VERSION}" \
    poetry==${PYTHON_POETRY_VERSION} \
    python-inspector=="${PYTHON_INSPECTOR_VERSION}"

#------------------------------------------------------------------------
# RUBY - Build Ruby as a separate component with rbenv
FROM build as rubybuild

ARG COCOAPODS_VERSION=1.11.2
ARG RUBY_VERSION=3.1.2
ENV RBENV_ROOT=/opt/rbenv
ENV PATH=${RBENV_ROOT}/bin:${RBENV_ROOT}/shims/:${RBENV_ROOT}/plugins/ruby-build/bin:$PATH

RUN git clone --depth 1 https://github.com/rbenv/rbenv.git ${RBENV_ROOT}
RUN git clone --depth 1 https://github.com/rbenv/ruby-build.git "$(rbenv root)"/plugins/ruby-build
WORKDIR ${RBENV_ROOT}
RUN src/configure \
    && make -C src
RUN rbenv install ${RUBY_VERSION} -v \
    && rbenv global ${RUBY_VERSION} \
    && gem install bundler cocoapods:${COCOAPODS_VERSION}

COPY scripts/ruby.sh /etc/profile.d

#------------------------------------------------------------------------
# NODEJS - Build NodeJS as a separate component with nvm
FROM build AS nodebuild

ARG BOWER_VERSION=1.8.12
ARG NODEJS_VERSION=16.17.0
ARG NPM_VERSION=8.5.0
ARG NVM_DIR=/opt/nvm
ARG PNPM_VERSION=7.8.0
ARG YARN_VERSION=1.22.10

RUN git clone --depth 1 https://github.com/nvm-sh/nvm.git $NVM_DIR
RUN . $NVM_DIR/nvm.sh \
    && nvm install "${NODEJS_VERSION}" \
    && nvm alias default "${NODEJS_VERSION}" \
    && nvm use default \
    && npm install --global npm@$NPM_VERSION bower@$BOWER_VERSION pnpm@$PNPM_VERSION yarn@$YARN_VERSION

#------------------------------------------------------------------------
# RUST - Build as a separate component
FROM build AS rustbuild

ARG RUST_HOME=/opt/rust
ARG CARGO_HOME=${RUST_HOME}/cargo
ARG RUSTUP_HOME=${RUST_HOME}/rustup
ARG RUST_VERSION=1.64.0
RUN curl -ksSf https://sh.rustup.rs | sh -s -- -y --profile minimal --default-toolchain ${RUST_VERSION}

#------------------------------------------------------------------------
# GOLANG - Build as a separate component
FROM build AS gobuild

ARG GO_DEP_VERSION=0.5.4
ARG GO_VERSION=1.18.3
ENV GOPATH=/opt/go
RUN curl -L https://dl.google.com/go/go${GO_VERSION}.linux-amd64.tar.gz | tar -C /opt -xz
ENV PATH=/opt/go/bin:$PATH
RUN go version
RUN curl -ksS https://raw.githubusercontent.com/golang/dep/v$GO_DEP_VERSION/install.sh | bash
RUN echo "add_local_path /opt/go/bin:\$PATH" > /etc/profile.d/go.sh

#------------------------------------------------------------------------
# HASKELL STACK
FROM build AS haskellbuild

ARG HASKELL_STACK_VERSION=2.7.5
RUN curl -sSL https://get.haskellstack.org/ | bash -s -- -d /usr/bin

#------------------------------------------------------------------------
# REPO / ANDROID SDK
FROM build AS androidbuild

ARG ANDROID_CMD_VERSION=8512546
ENV ANDROID_HOME=/opt/android-sdk

RUN curl -ksS https://storage.googleapis.com/git-repo-downloads/repo > /usr/bin/repo \
    && chmod a+x /usr/bin/repo

RUN curl -Os https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip \
    && unzip -q commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip -d $ANDROID_HOME \
    && rm commandlinetools-linux-${ANDROID_CMD_VERSION}_latest.zip \
    && PROXY_HOST_AND_PORT=${https_proxy#*://} \
    && if [ -n "$PROXY_HOST_AND_PORT" ]; then \
    # While sdkmanager uses HTTPS by default, the proxy type is still called "http".
    SDK_MANAGER_PROXY_OPTIONS="--proxy=http --proxy_host=${PROXY_HOST_AND_PORT%:*} --proxy_port=${PROXY_HOST_AND_PORT##*:}"; \
    fi \
    && yes | $ANDROID_HOME/cmdline-tools/bin/sdkmanager $SDK_MANAGER_PROXY_OPTIONS \
    --sdk_root=$ANDROID_HOME "platform-tools" "cmdline-tools;latest" \
    && chmod -R o+rw $ANDROID_HOME
COPY scripts/android.sh /etc/profile.d


#------------------------------------------------------------------------
# Main container
FROM base-image as run

# Python
ARG PYENV_ROOT=/opt/python
COPY --chown=$USERNAME:$USERNAME --from=pythonbuild ${PYENV_ROOT} ${PYENV_ROOT}
COPY --from=pythonbuild /etc/profile.d/python.sh /etc/profile.d/
RUN chmod o+rwx ${PYENV_ROOT}

# Ruby
ARG RBENV_ROOT=/opt/rbenv/
COPY --chown=$USERNAME:$USERNAME --from=rubybuild ${RBENV_ROOT} ${RBENV_ROOT}
COPY --from=rubybuild /etc/profile.d/ruby.sh /etc/profile.d/
RUN chmod o+rwx ${RBENV_ROOT}

# NodeJS
ARG NODEJS_VERSION=16.17.0
ARG NVM_DIR=/opt/nvm
ENV NODE_PATH $NVM_DIR/v$NODEJS_VERSION/lib/node_modules
ENV PATH $NVM_DIR/versions/node/v$NODEJS_VERSION/bin:$PATH
COPY --chown=$USERNAME:$USERNAME --from=nodebuild ${NVM_DIR} ${NVM_DIR}
RUN chmod o+rwx ${NVM_DIR}

# Rust
ARG RUST_HOME=/opt/rust
ARG CARGO_HOME=${RUST_HOME}/cargo
COPY --chown=$USERNAME:$USERNAME --from=rustbuild /opt/rust /opt/rust
COPY scripts/rust.sh /etc/profile.d/
RUN chmod o+rwx ${CARGO_HOME}

# Golang
COPY --chown=$USERNAME:$USERNAME --from=gobuild /opt/go /opt/go/
COPY --from=gobuild  /etc/profile.d/go.sh /etc/profile.d/

# Haskell
COPY --from=haskellbuild /usr/bin/stack /usr/bin

# Repo and Android
ENV ANDROID_HOME=/opt/android-sdk
COPY --from=androidbuild /usr/bin/repo /usr/bin/
COPY --from=androidbuild /etc/profile.d/android.sh /etc/profile.d/
COPY --chown=$USERNAME:$USERNAME --from=androidbuild ${ANDROID_HOME} ${ANDROID_HOME}
RUN chmod o+rw ${ANDROID_HOME}

# External repositories for SBT
ARG SBT_VERSION=1.6.1
RUN KEYURL="https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" \
    && echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list \
    && echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list \
    && curl -ksS "$KEYURL" | gpg --dearmor | tee "/etc/apt/trusted.gpg.d/scala_ubuntu.gpg" > /dev/null

# External repository for Dart
RUN KEYURL="https://dl-ssl.google.com/linux/linux_signing_key.pub" \
    && LISTURL="https://storage.googleapis.com/download.dartlang.org/linux/debian/dart_stable.list" \
    && curl -ksS "$KEYURL" | gpg --dearmor | tee "/etc/apt/trusted.gpg.d/dart.gpg" > /dev/null \
    && curl -ksS "$LISTURL" > /etc/apt/sources.list.d/dart.list \
    && echo "add_local_path /usr/lib/dart/bin:\$PATH" > /etc/profile.d/dart.sh

# Apt install commands.
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        dart \
        php \
        sbt=$SBT_VERSION \
        subversion \
    && rm -rf /var/lib/apt/lists/*

# PHP composer
ARG COMPOSER_VERSION=2.2
RUN curl -ksS https://getcomposer.org/installer | php -- --install-dir=/bin --filename=composer --$COMPOSER_VERSION

USER $USERNAME
WORKDIR $HOMEDIR

ENTRYPOINT ["/usr/bin/bash"]
