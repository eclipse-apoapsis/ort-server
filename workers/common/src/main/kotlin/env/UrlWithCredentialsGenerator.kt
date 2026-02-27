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

import kotlin.reflect.KClass

import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.workers.common.ResolvedInfrastructureService
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.BazelDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition

/**
 * A sealed class representing the different types of credential files that can be used to generate credential files.
 * [fileName] is the name of the credentials file and [definitionClass] is the associated
 * [EnvironmentServiceDefinition].
 */
sealed class CredentialFile<T : EnvironmentServiceDefinition>(
    val fileName: String,
    val definitionClass: KClass<T>
) {
    data object GitCredentialsFile :
        CredentialFile<EnvironmentServiceDefinition>(".git-credentials", EnvironmentServiceDefinition::class)

    data object BazelCredentialsFile :
        CredentialFile<BazelDefinition>(".bazel-credentials", BazelDefinition::class)
}

/**
 * A specialized generator class to generate files with special credentials for Git or Bazel.
 *
 * This generator is required only for corner cases. Normally, the Git CLI should be able to obtain credentials from
 * the _.netrc_ file. However, in some special cases, this mechanism is not working, and authentication against the
 * repository is only possible if the credentials are placed in a _.git-credentials_ file. This generator is able to
 * produce such a file, together with a Git configuration file that references it. It only processes infrastructure
 * services whose credential types contain [CredentialsType.GIT_CREDENTIALS_FILE].
 * For Bazel, this generator produces a _.bazel-credentials_ file, which is consumed by the Bazel Credentials Helper.
 *
 * There is a dependency to the [GitConfigGenerator] class, which generates a `credential` section in the Git
 * configuration file _.gitconfig_ to reference the generated _.git-credentials_ file.
 *
 * @param T The type of the [EnvironmentServiceDefinition] that this generator can process.
 */
class UrlWithCredentialsGenerator<T : EnvironmentServiceDefinition>(
    private val credentialFile: CredentialFile<T>
) : EnvironmentConfigGenerator<T> {
    /**
     * Return a string with the URL of this [ResolvedInfrastructureService] with the credentials embedded as needed
     * within the _.git-credentials_ file. Use [builder] to obtain secret references. Return *null* if the URL is
     * invalid.
     */
    internal fun ResolvedInfrastructureService.urlWithCredentials(
        builder: ConfigFileBuilder,
        credentialFile: CredentialFile<T>
    ): String? =
        runCatching {
            val serviceUrl = URI.create(url).toURL()

            GeneratorLogger.entryAdded(
                "${serviceUrl.protocol}://<username>:<password>@${serviceUrl.authority}${serviceUrl.path}",
                credentialFile.fileName,
                this
            )

            buildString {
                append(serviceUrl.protocol)
                append("://")
                append(builder.secretRef(usernameSecret, ConfigFileBuilder.urlEncoding)).append(':')
                append(builder.secretRef(passwordSecret, ConfigFileBuilder.urlEncoding)).append('@')
                append(serviceUrl.authority)
                append(serviceUrl.path)
            }
        }.onFailure {
            GeneratorLogger.error("Invalid URL for service '$this'. Ignoring it.", credentialFile.fileName, it)
        }.getOrNull()

    override val environmentDefinitionType: Class<T> =
        credentialFile.definitionClass.java

    override suspend fun generate(builder: ConfigFileBuilder, definitions: Collection<T>) {
        definitions.filter {
            (CredentialsType.GIT_CREDENTIALS_FILE in it.credentialsTypes()).takeIf {
                credentialFile == CredentialFile.GitCredentialsFile
            } ?: true
        }.takeUnless { it.isEmpty() }?.let {
            generateGitCredentials(builder, it)
        }
    }

    /**
     * Generate the content of the credential file using the given [builder] for the given [definitions].
     */
    private suspend fun generateGitCredentials(
        builder: ConfigFileBuilder,
        definitions: Collection<EnvironmentServiceDefinition>
    ) {
        builder.buildInUserHome(credentialFile.fileName) {
            definitions.mapNotNull { it.service.urlWithCredentials(builder, credentialFile) }
                .forEach(this::println)
        }
    }
}
