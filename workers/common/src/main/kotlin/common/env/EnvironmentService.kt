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

import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.repositories.InfrastructureServiceRepository
import org.ossreviewtoolkit.server.workers.common.context.WorkerContext

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

    /** The generator for _.netrc_ files. */
    private val netRcGenerator: NetRcGenerator
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
     * Generate the _.netrc_ file based on the given [services]. Use the given [context] to access required
     * information.
     */
    suspend fun generateNetRcFile(context: WorkerContext, services: Collection<InfrastructureService>): File {
        return netRcGenerator.targetFile().apply {
            val content = netRcGenerator.generate(context, services).joinToString(separator = System.lineSeparator())
            writeText(content)
        }
    }
}
