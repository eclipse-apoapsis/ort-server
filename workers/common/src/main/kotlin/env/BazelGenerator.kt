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
 * This configuration consists of:
 * - a _.bazel-credentials_ file. This file, which follows the same syntax as Git's _.git-credentials_, is read by the
 * Bazel Credentials Helper to read credentials for accessing remote artifactories.
 * This file is non-standard and not supported by Bazel itself.
 * - a .bazelrc file in the home directory enabling the use of the Bazel Credential Helper.
 * - the Bazel Credential Helper binary and its wrapper script:
 * this is required to make use of this configuration and should be installed under the path `/opt/bazel/`.
 * See https://github.com/eclipse-apoapsis/ort-server-credential-helper for releases of this binary.
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
