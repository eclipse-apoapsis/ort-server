# This Dockerfile is the base image for the Evaluator Docker image.

# syntax=docker/dockerfile:1.4

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

# When updating this version make sure to keep it in sync with the other worker Dockerfiles and libs.version.toml.
FROM eclipse-temurin:17.0.10_7-jdk-jammy@sha256:c8c2aa6fec59bce2caa631fff2a895a35645cd728a2b62ec7562694a0a7f917d

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

ENTRYPOINT ["/bin/bash"]
