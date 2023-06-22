/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.common.env.config

import com.charleskorn.kaml.Yaml

import java.io.File

import org.ossreviewtoolkit.server.model.Hierarchy
import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.repositories.SecretRepository

import org.slf4j.LoggerFactory

/**
 * A class for reading the environment configuration file from an analyzed repository. This configuration file
 * defines the services needed by the repository, such as source code and artifact repositories, and in which
 * configuration files they must be referenced. Based on this information, an environment can be constructed in which
 * the repository can be analyzed.
 *
 * The configuration file contains the following elements:
 * - The `strict` flag. The flag determines how (non-critical) errors in the configuration file should be handled.
 *   Such errors are typically caused by unresolvable references, e.g. to a non-existing secret or service. If set to
 *   *true* (which is the default), they cause the run to fail with a corresponding error message. If set to *false*,
 *   only a warning is logged, the affected declaration is ignored, and the analysis continues. This is likely to
 *   cause issues later when repositories cannot be accessed.
 * - A list with the infrastructure services specific to this repository. These are services only used by this
 *   repository and that have not been declared on the product or organization level.
 * - A list with environment definitions. Such definitions can reference infrastructure services (either declared in
 *   this configuration file or assigned to the owning product or organization) and specify the context in which
 *   those are used (e.g. in a Maven `settings.xml` file, in a `npmrc` file, etc.). It is also possible to declare
 *   environment variables and their values.
 *
 * An example configuration file could look as follows:
 *
 * ```
 * strict: false
 * infrastructure_services:
 * - name: "JFrog"
 *   url: "https://artifactory.example.org/repositories"
 *   description: "Main repository for releases."
 *   usernameSecret: "frogUsername"
 *   passwordSecret: "frogPassword"
 * ```
 */
class EnvironmentConfigLoader(
    /** The repository for secrets. This is used to resolve secret references. */
    private val secretRepository: SecretRepository
) {
    companion object {
        /** The path to the environment configuration file relative to the root folder of the repository. */
        const val CONFIG_FILE_PATH = ".ort.env.yml"

        private val logger = LoggerFactory.getLogger(EnvironmentConfigLoader::class.java)
    }

    /**
     * Read the environment configuration file from the repository defined by the given [Hierarchy] checked out at
     * the given [repositoryFolder] and return an [EnvironmentConfig] with its content. Syntactic errors in the file
     * cause exceptions to be thrown. Semantic errors are handled according to the `strict` flag.
     */
    fun parse(repositoryFolder: File, hierarchy: Hierarchy): EnvironmentConfig =
        repositoryFolder.resolve(CONFIG_FILE_PATH).takeIf { it.isFile }?.let { configFile ->
            logger.info("Parsing environment configuration file '{}'.", configFile)

            configFile.inputStream().use { stream ->
                val config = Yaml.default.decodeFromStream(RepositoryEnvironmentConfig.serializer(), stream)
                val services = parseServices(config, hierarchy)
                EnvironmentConfig(services)
            }
        } ?: EnvironmentConfig(emptyList())

    /**
     * Parse the infrastructure services defined in the given [config] and return a list with data objects for them.
     * Use the given [hierarchy] to resolve references.
     */
    private fun parseServices(config: RepositoryEnvironmentConfig, hierarchy: Hierarchy): List<InfrastructureService> {
        val secrets = resolveSecrets(config, hierarchy)

        return config.infrastructureServices.mapNotNull { service ->
            secrets[service.usernameSecret]?.let { usernameSecret ->
                secrets[service.passwordSecret]?.let { passwordSecret ->
                    InfrastructureService(
                        service.name,
                        service.url,
                        service.description,
                        usernameSecret,
                        passwordSecret,
                        null,
                        null
                    )
                }
            }
        }
    }

    /**
     * Resolve all the secrets referenced from infrastructure services in the given [config] in the given
     * [hierarchy] of the current repository. Return a [Map] with the resolved secrets keyed by their names.
     * Depending on the strict flag, fail if secrets cannot be resolved.
     */
    private fun resolveSecrets(config: RepositoryEnvironmentConfig, hierarchy: Hierarchy): Map<String, Secret> {
        val allSecretsNames = mutableSetOf<String>()
        config.infrastructureServices.forEach { service ->
            allSecretsNames += service.usernameSecret
            allSecretsNames += service.passwordSecret
        }

        val resolvedSecrets = mutableMapOf<String, Secret>()

        fun fetchSecrets(fetcher: () -> List<Secret>) {
            if (allSecretsNames.isNotEmpty()) {
                val secrets = fetcher()

                val secretsMap = secrets.associateBy(Secret::name)
                resolvedSecrets += secretsMap
                allSecretsNames -= secretsMap.keys
            }
        }

        fetchSecrets { secretRepository.listForRepository(hierarchy.repository.id) }
        fetchSecrets { secretRepository.listForProduct(hierarchy.product.id) }
        fetchSecrets { secretRepository.listForOrganization(hierarchy.organization.id) }

        if (allSecretsNames.isNotEmpty()) {
            val message = "Invalid secret names. The following names cannot be resolved: $allSecretsNames"
            if (config.strict) {
                throw EnvironmentConfigException(message)
            } else {
                logger.warn(message)
            }
        }

        return resolvedSecrets
    }
}

/**
 * An exception class for reporting problems with the environment configuration.
 */
class EnvironmentConfigException(message: String) : Exception(message)
