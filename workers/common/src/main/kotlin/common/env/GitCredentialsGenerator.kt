/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import java.net.URI

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition

import org.slf4j.LoggerFactory

/**
 * A specialized generator class to generate files with special credentials for Git.
 *
 * This generator is required only for corner cases. Normally, the Git CLI should be able to obtain credentials from
 * the _.netrc_ file. However, in some special cases, this mechanism is not working, and authentication against the
 * repository is only possible if the credentials are placed in a _.git-credentials_ file. This generator is able to
 * produce such a file, together with a Git configuration file that references it. It only processes infrastructure
 * services whose credentials types contain [CredentialsType.GIT_CREDENTIALS_FILE].
 *
 * There is a dependency to the [GitConfigGenerator] class, which generates a `credential` section in the Git
 * configuration file _.gitconfig_ in order to reference the generated _.git-credentials_ file.
 */
class GitCredentialsGenerator : EnvironmentConfigGenerator<EnvironmentServiceDefinition> {
    companion object {
        /** The name of the file storing the actual credentials. */
        private const val GIT_CREDENTIALS_FILE_NAME = ".git-credentials"

        private val logger = LoggerFactory.getLogger(GitCredentialsGenerator::class.java)

        /**
         * Return a string with the URL of this [InfrastructureService] with the credentials embedded as needed within
         * the _.git-credentials_ file. Use [builder] to obtain secret references. Return *null* if the URL is invalid.
         */
        private fun InfrastructureService.urlWithCredentials(builder: ConfigFileBuilder): String? = runCatching {
            buildString {
                val serviceUrl = URI.create(url).toURL()
                append(serviceUrl.protocol)
                append("://")
                append(builder.secretRef(usernameSecret)).append(':')
                append(builder.secretRef(passwordSecret)).append('@')
                append(serviceUrl.authority)
                append(serviceUrl.path)
            }
        }.onFailure {
            logger.error("Invalid URL for service '{}'. Ignoring it.", this, it)
        }.getOrNull()
    }

    override val environmentDefinitionType: Class<EnvironmentServiceDefinition> =
        EnvironmentServiceDefinition::class.java

    override suspend fun generate(builder: ConfigFileBuilder, definitions: Collection<EnvironmentServiceDefinition>) {
        definitions.filter {
            CredentialsType.GIT_CREDENTIALS_FILE in it.credentialsTypes()
        }.takeUnless { it.isEmpty() }?.let {
            generateGitCredentials(builder, it)
        }
    }

    /**
     * Generate the content of the _.git-credentials_ file using the given [builder] for the given [definitions].
     */
    private suspend fun generateGitCredentials(
        builder: ConfigFileBuilder,
        definitions: Collection<EnvironmentServiceDefinition>
    ) {
        builder.buildInUserHome(GIT_CREDENTIALS_FILE_NAME) {
            definitions.mapNotNull { it.service.urlWithCredentials(builder) }
                .forEach(this::println)
        }
    }
}
