/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.common.env.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.decodeFromStream

import java.io.File

import org.eclipse.apoapsis.ortserver.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.model.EnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentVariableDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.RepositoryEnvironmentVariableDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SecretVariableDefinition
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.SimpleVariableDefinition

import org.ossreviewtoolkit.utils.common.alsoIfNull

import org.slf4j.LoggerFactory

/**
 * A class for reading the environment configuration file from an analyzed repository. This configuration file
 * defines the services needed by the repository, such as source code and artifact repositories, and in which
 * configuration files they must be referenced. Based on this information, an environment can be constructed in which
 * the repository can be analyzed.
 *
 * The configuration file contains the following elements:
 * - The `strict` flag. The flag determines how (non-critical) errors in the configuration file should be handled.
 *   Such errors are typically caused by unresolvable references, e.g., to a non-existing secret or service. If set to
 *   *true* (which is the default), they cause the run to fail with a corresponding error message. If set to *false*,
 *   only a warning is logged, the affected declaration is ignored, and the analysis continues. This is likely to
 *   cause issues later when repositories cannot be accessed.
 * - A list with the infrastructure services specific to this repository. These are services only used by this
 *   repository, and that have not been declared on the product or organization level.
 * - A list with environment definitions. Such definitions can reference infrastructure services (either declared in
 *   this configuration file or assigned to the owning product or organization) and specify the context in which
 *   those are used (e.g., in a Maven `settings.xml` file, in a `npmrc` file, etc.). It is also possible to declare
 *   environment variables and their values.
 * - A list with environment variables. These are environment variables that must be set when analyzing the repository.
 *   The values of these variables are obtained from secrets.
 *
 * An example configuration file could look as follows:
 *
 * ```
 * strict: false
 * infrastructureServices:
 * - name: "JFrog"
 *   url: "https://artifactory.example.org/repositories"
 *   description: "Main repository for releases."
 *   usernameSecret: "frogUsername"
 *   passwordSecret: "frogPassword"
 *   credentialsTypes:
 *   - "NETRC"
 * environmentDefinitions:
 *   maven:
 *   - service: "JFrog"
 *     id: "releasesRepo"
 * environmentVariables:
 * - name: "REPOSITORY_PASSWORD"
 *   secretName: "frogPassword"
 * ```
 */
class EnvironmentConfigLoader(
    /** The repository for secrets. This is used to resolve secret references. */
    private val secretRepository: SecretRepository,

    /**
     * The repository for infrastructure services. This is needed to resolve references to services that are not
     * defined in the configuration itself, but for the current product or organization.
     */
    private val serviceRepository: InfrastructureServiceRepository,

    /** The factory for creating environment definitions. */
    private val definitionFactory: EnvironmentDefinitionFactory
) {
    companion object {
        /** The default path to the environment configuration file relative to the root folder of the repository. */
        const val DEFAULT_CONFIG_FILE_PATH = ".ort.env.yml"

        private val logger = LoggerFactory.getLogger(EnvironmentConfigLoader::class.java)
    }

    /**
     * Read the environment configuration file [environmentConfigPath] from the repository defined by the given
     * [Hierarchy] checked out at the given [repositoryFolder] and return an [ResolvedEnvironmentConfig] with its
     * content. If not file path is provided, the default path `.ort.env.yml` is used. Syntactic errors in the file
     * cause exceptions to be thrown. Semantic errors are handled according to the `strict` flag.
     */
    fun resolveAndParse(repositoryFolder: File, environmentConfigPath: String? = null): EnvironmentConfig =
        resolveEnvironmentConfigFile(repositoryFolder, environmentConfigPath)?.let { config ->
            parseEnvironmentConfigFile(config)
        } ?: EnvironmentConfig()

    /**
     * Resolve the environment configuration file [environmentConfigPath] in the given folder [repositoryFolder]. If
     * the given file path does not exist or is 'null', the default file path `.ort.env.yml` is used instead. Return the
     * resolved file.
     */
    internal fun resolveEnvironmentConfigFile(repositoryFolder: File, environmentConfigPath: String? = null): File? {
        val customEnvironmentConfigFile = environmentConfigPath?.let {
            repositoryFolder.resolve(it).takeIf { file -> file.isFile }.alsoIfNull {
                logger.warn("Custom environment configuration file '$environmentConfigPath' not found.")
            }
        }

        val defaultEnvironmentConfigFile = if (customEnvironmentConfigFile == null) {
            repositoryFolder.resolve(DEFAULT_CONFIG_FILE_PATH).takeIf { it.isFile }.alsoIfNull {
                logger.info("Default environment configuration file '$DEFAULT_CONFIG_FILE_PATH' not found.")
            }
        } else {
            null
        }

        return (customEnvironmentConfigFile ?: defaultEnvironmentConfigFile)
    }

    /**
     * Parse the environment configuration file [environmentConfigFile] and return the content as an
     * [ResolvedEnvironmentConfig].
     */
    internal fun parseEnvironmentConfigFile(environmentConfigFile: File): EnvironmentConfig {
        logger.info("Parsing environment configuration file '{}'.", environmentConfigFile)

        return environmentConfigFile.inputStream().use { stream ->
            Yaml.default.decodeFromStream(EnvironmentConfig.serializer(), stream)
        }
    }

    /**
     * Resolve the given [config] for the repository defined by the given [hierarchy]. This function is used when the
     * environment configuration for the repository is not read from the source code, but passed when triggering the
     * run.
     */
    fun resolve(config: EnvironmentConfig, hierarchy: Hierarchy): ResolvedEnvironmentConfig {
        val repositoryConfig = RepositoryEnvironmentConfig(
            infrastructureServices = config.infrastructureServices.map { it.toRepositoryService() },
            environmentDefinitions = config.environmentDefinitions,
            environmentVariables = config.environmentVariables.map { it.toRepositoryVariable() },
            strict = config.strict
        )

        return resolveRepositoryEnvironmentConfig(repositoryConfig, hierarchy)
    }

    /**
     * Resolve the declarations in the given [config] using the provided [hierarchy]. Handle references that cannot be
     * resolved according to the `strict` flag in the configuration.
     */
    private fun resolveRepositoryEnvironmentConfig(
        config: RepositoryEnvironmentConfig,
        hierarchy: Hierarchy
    ): ResolvedEnvironmentConfig {
        val secrets = resolveSecrets(config, hierarchy)
        val services = parseServices(config, secrets)
        val definitions = parseEnvironmentDefinitions(config, hierarchy, services)
        val variables = parseEnvironmentVariables(config, secrets)

        return ResolvedEnvironmentConfig(services, definitions, variables)
    }

    /**
     * Parse the infrastructure services defined in the given [config] and return a list with data objects for them.
     * Use the given map with [secrets] to resolve references to secrets.
     */
    private fun parseServices(
        config: RepositoryEnvironmentConfig,
        secrets: Map<String, Secret>
    ): List<InfrastructureService> =
        config.infrastructureServices.mapNotNull { service ->
            secrets[service.usernameSecret]?.let { usernameSecret ->
                secrets[service.passwordSecret]?.let { passwordSecret ->
                    InfrastructureService(
                        service.name,
                        service.url,
                        service.description,
                        usernameSecret,
                        passwordSecret,
                        null,
                        null,
                        service.credentialsTypes
                    )
                }
            }
        }

    /**
     * Resolve all the secrets referenced from elements in the given [config] in the given [hierarchy] of the current
     * repository. Return a [Map] with the resolved secrets keyed by their names. Depending on the strict flag, fail
     * if secrets cannot be resolved.
     */
    private fun resolveSecrets(config: RepositoryEnvironmentConfig, hierarchy: Hierarchy): Map<String, Secret> {
        val allSecretsNames = mutableSetOf<String>()
        allSecretsNames += config.environmentVariables.mapNotNull { it.secretName }
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

        fetchSecrets { secretRepository.listForId(RepositoryId(hierarchy.repository.id)).data }
        fetchSecrets { secretRepository.listForId(ProductId(hierarchy.product.id)).data }
        fetchSecrets { secretRepository.listForId(OrganizationId(hierarchy.organization.id)).data }

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

    /**
     * Parse the environment service definitions declared in the given [config] and convert them to corresponding data
     * objects. Resolve service references based on the given [configServices] (the services that have already been
     * parsed from the configuration file) or from the product or organization defined by the given [hierarchy].
     */
    private fun parseEnvironmentDefinitions(
        config: RepositoryEnvironmentConfig,
        hierarchy: Hierarchy,
        configServices: List<InfrastructureService>
    ): List<EnvironmentServiceDefinition> {
        val serviceResolver = ServiceResolver(hierarchy, serviceRepository, configServices)

        val definitionResults = config.environmentDefinitions.entries.flatMap { entry ->
            entry.value.map { definition ->
                serviceResolver.resolveService(definition).mapCatching { service ->
                    definitionFactory.createDefinition(entry.key, service, definition).getOrThrow()
                }
            }
        }

        val (successes, failures) = definitionResults.partition { it.isSuccess }.run {
            first.mapNotNull { it.getOrNull() } to second.mapNotNull { it.exceptionOrNull() }
        }

        handleInvalidDefinitions(config, failures)

        return successes
    }

    /**
     * Check whether there are invalid environment service definitions in the given [config] based on the given
     * [failures] list. If so, generate a meaningful message and either throw an exception (in strict mode) or log a
     * warning.
     */
    private fun handleInvalidDefinitions(config: RepositoryEnvironmentConfig, failures: List<Throwable>) {
        if (failures.isEmpty()) return

        val message = buildString {
            appendLine("Found invalid environment service definitions:")
            failures.forEach(::appendLine)
        }

        if (config.strict) {
            throw EnvironmentConfigException(message)
        } else {
            logger.warn(message)
        }
    }

    /**
     * Parse the environment variables defined in the given [config] and return a set with data objects for them. Use
     * the given map with [secrets] to resolve references to secrets.
     */
    private fun parseEnvironmentVariables(
        config: RepositoryEnvironmentConfig,
        secrets: Map<String, Secret>
    ): Set<EnvironmentVariableDefinition> {
        fun isValidVariable(variable: RepositoryEnvironmentVariableDefinition): Boolean {
            return when {
                !variable.secretName.isNullOrEmpty() && !variable.value.isNullOrEmpty() -> false
                !variable.secretName.isNullOrEmpty() -> variable.secretName in secrets
                !variable.value.isNullOrEmpty() -> true
                else -> false
            }
        }
        val (validVariables, invalidVariables) = config.environmentVariables.partition { isValidVariable(it) }

        handleInvalidVariables(config, invalidVariables)

        return validVariables.map { definition ->
            when {
                definition.value != null -> {
                    SimpleVariableDefinition(definition.name, definition.value)
                }
                definition.secretName != null -> {
                    val secret = secrets[definition.secretName] ?: throw EnvironmentConfigException(
                        "Unknown secret: '${definition.secretName}'."
                    )
                    SecretVariableDefinition(definition.name, secret)
                }
                else -> throw EnvironmentConfigException("Invalid environment variable definition: $definition")
            }
        }.toSet()
    }

    /**
     * Check whether there are invalid environment variable definitions in the given [config] based on the given
     * [failures] list. If so, generate a meaningful message and either throw an exception (in strict mode) or log a
     * warning.
     */
    private fun handleInvalidVariables(
        config: RepositoryEnvironmentConfig,
        failures: List<RepositoryEnvironmentVariableDefinition>
    ) {
        if (failures.isEmpty()) return

        val message = buildString {
            appendLine("Found invalid environment variable definitions:")
            failures.forEach(::appendLine)
        }

        if (config.strict) {
            throw EnvironmentConfigException(message)
        } else {
            logger.warn(message)
        }
    }
}

/**
 * An exception class for reporting problems with the environment configuration.
 */
class EnvironmentConfigException(message: String) : Exception(message)

/**
 * A helper class for resolving services on different layers of the hierarchy.
 */
private class ServiceResolver(
    /** The [Hierarchy] of the current repository. */
    val hierarchy: Hierarchy,

    /** The repository for infrastructure services. */
    serviceRepository: InfrastructureServiceRepository,

    /** The list of services defined in the repository configuration file. */
    configServices: List<InfrastructureService>
) {
    /** A map for fast access to repository services. */
    private val repositoryServices by lazy { configServices.associateByName() }

    /** A map with the services defined for the current product. */
    private val productServices by lazy { serviceRepository.listForProduct(hierarchy.product.id).associateByName() }

    /** A map with the services defined for the current organization. */
    private val organizationServices by lazy {
        serviceRepository.listForOrganization(hierarchy.organization.id).data.associateByName()
    }

    /**
     * Try to resolve the [InfrastructureService] referenced by the environment service definition with the given
     * [properties].
     */
    fun resolveService(properties: Map<String, String>): Result<InfrastructureService> = runCatching {
        val serviceName = properties[EnvironmentDefinitionFactory.SERVICE_PROPERTY]
            ?: throw EnvironmentConfigException("Missing service reference: $properties")

        repositoryServices[serviceName]
            ?: productServices[serviceName]
            ?: organizationServices[serviceName]
            ?: throw EnvironmentConfigException("Unknown service: '$serviceName'.")
    }
}

/**
 * Return a [Map] with the [InfrastructureService]s contained in this [Collection] using the service names as keys.
 * This is useful when resolving the services referenced by environment definitions.
 */
private fun Collection<InfrastructureService>.associateByName(): Map<String, InfrastructureService> =
    associateBy(InfrastructureService::name)

/**
 * Convert this [InfrastructureServiceDeclaration] to a [RepositoryInfrastructureService].
 */
private fun InfrastructureServiceDeclaration.toRepositoryService(): RepositoryInfrastructureService =
    RepositoryInfrastructureService(name, url, description, usernameSecret, passwordSecret, credentialsTypes)

/**
 * Convert this [EnvironmentVariableDeclaration] to a [RepositoryEnvironmentVariableDefinition].
 */
private fun EnvironmentVariableDeclaration.toRepositoryVariable(): RepositoryEnvironmentVariableDefinition =
    RepositoryEnvironmentVariableDefinition(name, secretName, value)
