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

package org.ossreviewtoolkit.server.workers.common.env

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.repositories.InfrastructureServiceRepository
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext
import org.ossreviewtoolkit.server.workers.common.env.config.EnvironmentConfigLoader
import org.ossreviewtoolkit.server.workers.common.env.config.ResolvedEnvironmentConfig
import org.ossreviewtoolkit.server.workers.common.env.definition.EnvironmentServiceDefinition

/**
 * A service class providing functionality for setting up the build environment when running a worker.
 *
 * When executing ORT logic - especially the analyzer and the scanner - some important configuration files for the
 * underlying tools must be available, so that external repositories can be accessed and dependencies can be
 * downloaded. The exact content of these configuration files is determined dynamically for each individual ORT run
 * based on settings and configurations assigned to the current organization, product, and repository.
 *
 * This service can be used by workers to prepare the environment before their execution.
 */
class EnvironmentService(
    /** The repository for accessing infrastructure services. */
    private val infrastructureServiceRepository: InfrastructureServiceRepository,

    /** A collection with the supported generators for configuration files. */
    private val generators: Collection<EnvironmentConfigGenerator<*>>,

    /** The helper object for loading the environment configuration file. */
    private val configLoader: EnvironmentConfigLoader
) {
    /**
     * Try to find the [InfrastructureService] that matches the current repository stored in the given [context]. This
     * function is used to find the credentials for downloading the repository. The match is done based on the URL
     * prefix. In case there are multiple matches, the longest match wins.
     */
    fun findInfrastructureServiceForRepository(context: WorkerContext): InfrastructureService? =
        with(context.hierarchy) {
            infrastructureServiceRepository.listForRepositoryUrl(repository.url, organization.id, product.id)
                .filter { repository.url.startsWith(it.url) }
                .maxByOrNull { it.url.length }
        }

    /**
     * Set up the analysis environment for the current repository defined by the given [context] that has been
     * checked out to the given [repositoryFolder]. The credentials of this repository - if any - are defined by the
     * given [repositoryService].
     */
    suspend fun setUpEnvironment(
        context: WorkerContext,
        repositoryFolder: File,
        repositoryService: InfrastructureService?
    ): ResolvedEnvironmentConfig {
        val config = configLoader.parse(repositoryFolder, context.hierarchy)

        val allServices = config.environmentDefinitions.mapTo(mutableSetOf()) { it.service }
        allServices += config.infrastructureServices
        repositoryService?.let { allServices += it }
        assignServicesToOrtRun(context, allServices)

        generateConfigFiles(context, config.environmentDefinitions)

        return config
    }

    /**
     * Generate all configuration files supported by the managed [EnvironmentConfigGenerator]s based on the passed in
     * [definitions]. Use the given [context] to access required information.
     */
    private suspend fun generateConfigFiles(
        context: WorkerContext,
        definitions: Collection<EnvironmentServiceDefinition>
    ) =
        withContext(Dispatchers.IO) {
            generators.map { generator ->
                val builder = ConfigFileBuilder(context)
                async { generator.generateApplicable(builder, definitions) }
            }.awaitAll()
        }

    /**
     * Generate the _.netrc_ file based on the given [services]. Use the given [context] to access required
     * information. This is a special variant of the [generateConfigFiles] function that focuses only on the
     * _.netrc_ file.
     */
    suspend fun generateNetRcFile(context: WorkerContext, services: Collection<InfrastructureService>) {
        val definitions = services.map { EnvironmentServiceDefinition(it) }
        generateConfigFiles(context, definitions)
    }

    /**
     * Generate the _.netrc_ file for the current ORT run as defined by the given [context]. Load the infrastructure
     * services associated with the current run from the database and process their credentials. This function can be
     * used by workers running after the Analyzer, which has initialized the required information.
     */
    suspend fun generateNetRcFileForCurrentRun(context: WorkerContext) {
        generateNetRcFile(context, infrastructureServiceRepository.listForRun(context.ortRun.id))
    }

    /**
     * Update the database to record that the given [services] have been referenced from the current ORT run as
     * obtained from the given [context].
     */
    private fun assignServicesToOrtRun(context: WorkerContext, services: Collection<InfrastructureService>) {
        services.forEach { service ->
            infrastructureServiceRepository.getOrCreateForRun(service, context.ortRun.id)
        }
    }
}
