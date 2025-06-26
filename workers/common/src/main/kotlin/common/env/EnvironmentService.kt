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

package org.eclipse.apoapsis.ortserver.workers.common.env

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceDeclarationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.env.config.EnvironmentConfigLoader
import org.eclipse.apoapsis.ortserver.workers.common.env.config.ResolvedEnvironmentConfig
import org.eclipse.apoapsis.ortserver.workers.common.env.definition.EnvironmentServiceDefinition
import org.eclipse.apoapsis.ortserver.workers.common.resolvedConfigurationContext

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(EnvironmentService::class.java)

/**
 * A service class providing functionality for setting up the build environment when running a worker.
 *
 * When executing ORT logic - especially the analyzer and the scanner - some important configuration files for the
 * underlying tools must be available, so that external repositories can be accessed and dependencies can be
 * downloaded. The exact content of these configuration files is determined dynamically for each ORT run based on
 * settings and configurations assigned to the current organization, product, and repository.
 *
 * This service can be used by workers to prepare the environment before their execution.
 */
class EnvironmentService(
    /** The repository for accessing infrastructure services. */
    private val infrastructureServiceRepository: InfrastructureServiceRepository,

    /** The repository for accessing dynamic infrastructure services. */
    private val infrastructureServiceDeclarationRepository: InfrastructureServiceDeclarationRepository,

    /** The repository for secrets. This is used to resolve secret references. */
    private val secretRepository: SecretRepository,

    /** A collection with the supported generators for configuration files. */
    private val generators: Collection<EnvironmentConfigGenerator<*>>,

    /** The helper object for loading the environment configuration file. */
    private val configLoader: EnvironmentConfigLoader,

    /** The admin configuration service to access global settings. */
    private val adminConfigService: AdminConfigService
) {

    /**
     * Return a list of all [InfrastructureService]s that may be relevant for checking out the current repository
     * defined by the given [context]. If specified, merge this list with the services from the given [config], so
     * that the services from the [config] take priority over the ones from the database. The resulting services can
     * then be added to the authenticator to make sure that all credentials are available, even if the repository
     * contains submodules or other dependencies.
     */
    fun findInfrastructureServicesForRepository(
        context: WorkerContext,
        config: EnvironmentConfig?
    ): List<InfrastructureService> {
        val hierarchyServices = infrastructureServiceRepository.listForHierarchy(
            context.hierarchy.organization.id,
            context.hierarchy.product.id
        ).associateBy(InfrastructureService::url)

        val configServices = config?.let {
            configLoader.resolve(it, context.hierarchy).infrastructureServices
        }.orEmpty().associateBy(InfrastructureService::url)

        return (hierarchyServices + configServices).values.toList()
    }

    /**
     * Set up the analysis environment for the current repository defined by the given [context] that has been
     * checked out to the given [repositoryFolder]. The credentials of this repository - if any - are defined by the
     * given [repositoryServices]. If an optional [config] is provided, it will be merged with the parsed configuration.
     * In case of overlapping entries, the provided [config] will take priority over the parsed configuration.
     */
    suspend fun setUpEnvironment(
        context: WorkerContext,
        repositoryFolder: File,
        config: EnvironmentConfig?,
        repositoryServices: List<InfrastructureService>
    ): ResolvedEnvironmentConfig {
        val environmentConfigPath = context.ortRun.environmentConfigPath
        val mergedConfig = configLoader.resolveAndParse(repositoryFolder, environmentConfigPath).merge(config)
        val resolvedConfig = configLoader.resolve(mergedConfig, context.hierarchy)

        return setUpEnvironmentForConfig(context, resolvedConfig, repositoryServices)
    }

    /**
     * Set up the analysis environment based on the given resolved [config]. Use the given [context]. Also take the
     * given [repositoryServices] into account that have been used to download the repository.
     */
    private suspend fun setUpEnvironmentForConfig(
        context: WorkerContext,
        config: ResolvedEnvironmentConfig,
        repositoryServices: List<InfrastructureService>
    ): ResolvedEnvironmentConfig {
        val environmentServices = config.environmentDefinitions.map { it.service }
        val infraServices = config.infrastructureServices.toMutableSet()
        infraServices += repositoryServices

        val unreferencedServices = infraServices.filterNot { it in environmentServices }
        val allEnvironmentDefinitions = config.environmentDefinitions +
                unreferencedServices.map(::EnvironmentServiceDefinition)

        val adjustedServices = config.environmentDefinitions.mapTo(unreferencedServices.toMutableSet()) { definition ->
            definition.credentialsTypes?.let { definition.service.copy(credentialsTypes = it) }
                ?: definition.service
        }

        assignServicesToOrtRun(context, adjustedServices)

        generateConfigFiles(context, allEnvironmentDefinitions)

        return config
    }

    /**
     * Generate all configuration files supported by the managed [EnvironmentConfigGenerator]s based on the passed in
     * [definitions]. Use the given [context] to access required information.
     */
    private suspend fun generateConfigFiles(
        context: WorkerContext,
        definitions: Collection<EnvironmentServiceDefinition>
    ) {
        val adminConfig = adminConfigService.loadAdminConfig(
            context.resolvedConfigurationContext,
            context.ortRun.organizationId
        )
        val services = definitions.map(EnvironmentServiceDefinition::service)
        val netRcManager = NetRcManager.create(context.credentialResolverFun)
        context.setupAuthentication(services, netRcManager)

        withContext(Dispatchers.IO) {
            generators.map { generator ->
                val builder = ConfigFileBuilder(
                    adminConfig,
                    context.credentialResolverFun,
                    context.configManager::getSecret
                )
                async { generator.generateApplicable(builder, definitions) }
            }.awaitAll()
        }
    }

    /**
     * Perform the required steps to set up authentication information for the current worker execution based on the
     * given [services]. Use the given [context] to access required information. This is a special variant of the
     * [setUpEnvironment] function that focuses only on credentials and does not generate other package
     * manager-specific configuration files.
     */
    suspend fun setupAuthentication(context: WorkerContext, services: Collection<InfrastructureService>) {
        val definitions = services.map { EnvironmentServiceDefinition(it) }
        generateConfigFiles(context, definitions)
    }

    /**
     * Perform the required steps to set up authentication information for the current worker execution in the current
     * ORT run as defined by the given [context]. Load the infrastructure services associated with the current run from
     * the database. Given the secret names in [InfrastructureServiceDeclaration.usernameSecretName] and
     * [InfrastructureServiceDeclaration.passwordSecretName], find these secrets and continue to process them.
     * This function can be used by workers running after the Analyzer, which has initialized the required information.
     */
    suspend fun setupAuthenticationForCurrentRun(context: WorkerContext) {
        val infrastructureServiceDeclarations = infrastructureServiceDeclarationRepository.listForRun(context.ortRun.id)

        val infrastructureServices = withContext(Dispatchers.IO) {
            infrastructureServiceDeclarations.map { service ->
                async {
                    val usernameSecret = resolveSecretByName(
                        service.usernameSecret,
                        context.ortRun,
                        service.name
                    ) ?: error("Username secret ${service.usernameSecret} not found for service '${service.name}'.")

                    val passwordSecret = resolveSecretByName(
                        service.passwordSecret,
                        context.ortRun,
                        service.name
                    ) ?: error("Password secret ${service.passwordSecret} not found for service '${service.name}'.")

                    InfrastructureService(
                        name = service.name,
                        url = service.url,
                        description = service.description,
                        usernameSecret = usernameSecret,
                        passwordSecret = passwordSecret,
                        organization = null,
                        product = null,
                        credentialsTypes = service.credentialsTypes
                    )
                }
            }.awaitAll()
        }

        setupAuthentication(context, infrastructureServices)
    }

    /**
     * Update the database to record that the given [services] have been referenced from the current ORT run as
     * obtained from the given [context].
     */
    private fun assignServicesToOrtRun(context: WorkerContext, services: Collection<InfrastructureService>) {
        services.forEach { service ->
            infrastructureServiceDeclarationRepository.getOrCreateForRun(
                service.toInfrastructureServiceDeclaration(),
                context.ortRun.id
            )
        }
    }

    /**
     * Resolve a secret by its [secretName] for the current [ortRun]. The secret is searched in the repository, product,
     * and organization levels, in that order. If a secret is found at multiple levels, the one at the repository level
     * is preferred, followed by the product level, and finally the organization level. In case a secret is found at
     * multiple levels, a warning is logged about this ambiguity. If no secret is found at all, return null.
     */
    fun resolveSecretByName(secretName: String, ortRun: OrtRun, serviceName: String): Secret? {
        // Try to get from all 3 levels: repository, product, and organization
        val secretRepositoryLevel =
            secretRepository.getByIdAndName(RepositoryId(ortRun.repositoryId), secretName)
        val secretProductLevel =
            secretRepository.getByIdAndName(ProductId(ortRun.productId), secretName)
        val secretOrganizationLevel =
            secretRepository.getByIdAndName(OrganizationId(ortRun.organizationId), secretName)

        secretRepositoryLevel?.let {
            secretProductLevel?.let {
                secretOrganizationLevel?.let {
                    logger.info(
                        "Found secret '$secretName' at repository, product and organization level " +
                            "for service '$serviceName'. Using repository level."
                    )
                    return secretRepositoryLevel
                }

                logger.info(
                    "Found secret '$secretName' at repository and product level " +
                        "for service '$serviceName'. Using repository level."
                )
                return secretRepositoryLevel
            }
            secretOrganizationLevel?.let {
                logger.info(
                    "Found secret '$secretName' at repository and organization level " +
                        "for service '$serviceName'. Using repository level."
                )
                return secretRepositoryLevel
            }
            return secretRepositoryLevel
        }

        secretProductLevel?.let {
            secretOrganizationLevel?.let {
                logger.info(
                    "Found secret '$secretName' at product and organization level " +
                        "for service '$serviceName'. Using product level."
                )
                return secretProductLevel
            }
            return secretProductLevel
        }

        secretOrganizationLevel?.let {
            return secretOrganizationLevel
        }

        logger.error("No secret found by name '$secretName' for service '$serviceName'.")
        return null
    }
}

/**
 * Merge this [EnvironmentConfig] with another [EnvironmentConfig]. The merging process ensures that:
 * - Overlapping infrastructure services are overridden by the ones from the [other] config.
 * - Environment definitions are combined, with values from both configs being flattened.
 * - Environment variables with the same name are overridden by the ones from the [other] config.
 */
internal fun EnvironmentConfig.merge(other: EnvironmentConfig?): EnvironmentConfig {
    if (other == null) return this

    val (overridden, unreferenced) = infrastructureServices
        .partition { service -> other.infrastructureServices.any { it.name == service.name } }

    if (overridden.isNotEmpty()) {
        logger.info(
            "The following infrastructure services have been overridden: ${overridden.joinToString { it.name }}."
        )
    }

    val mergedInfrastructureService = unreferenced + other.infrastructureServices
    val mergedEnvironmentDefinitions =
        (environmentDefinitions.asSequence() + other.environmentDefinitions.asSequence())
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.flatten() }
    val mergedEnvironmentVariables = (environmentVariables + other.environmentVariables)
        .associateBy { it.name }
        .values
        .toList()

    return EnvironmentConfig(mergedInfrastructureService, mergedEnvironmentDefinitions, mergedEnvironmentVariables)
}
