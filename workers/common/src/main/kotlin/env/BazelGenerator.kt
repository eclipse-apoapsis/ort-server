/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */
package org.eclipse.apoapsis.ortserver.workers.common.env

import org.eclipse.apoapsis.ortserver.workers.common.env.definition.BazelDefinition

internal const val BAZEL_CREDENTIALS_FILE_NAME = ".bazel-credentials"
internal const val BAZEL_RC_FILE_NAME = ".bazelrc"

/**
 * A specialized generator class for creating the configuration necessary for Bazel authentication to artifactories.
 * Authorization for Bazel is handled by a custom Credential Helper
 * (https://github.com/EngFlow/credential-helper-spec/blob/main/spec.md) which is provided as a separate project of the
 * eclipse-apoapsis organization (https://github.com/eclipse-apoapsis/ort-server-credential-helper). This generator
 * class generates the file with the credentials to be consumed by this Credential Helper. The file uses the same syntax
 * as Git's .git-credentials file (https://git-scm.com/book/en/v2/Git-Tools-Credential-Storage). To activate the
 * Credential Helper in Bazel, it has to be referenced in a .bazelrc file in the home directory. This generator creates
 * this file as well. It assumes that the Credential Helper is installed under the path /opt/bazel - this has to be
 * aligned with the build of the container images.
 */
class BazelGenerator : EnvironmentConfigGenerator<BazelDefinition> {
    override val environmentDefinitionType: Class<BazelDefinition> = BazelDefinition::class.java

    override suspend fun generate(
        builder: ConfigFileBuilder,
        definitions: Collection<BazelDefinition>
    ) {
        builder.buildInUserHome(BAZEL_CREDENTIALS_FILE_NAME) {
            definitions.mapNotNull { it.service.urlWithCredentials(builder, BAZEL_CREDENTIALS_FILE_NAME) }
                .forEach(this::println)
        }

        builder.buildInUserHome(BAZEL_RC_FILE_NAME) {
            println("common --credential_helper=/opt/bazel/bazel_cred_wrapper.sh")
        }
    }
}
