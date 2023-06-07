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

import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.repositories.InfrastructureServiceRepository

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
    private val infrastructureServiceRepository: InfrastructureServiceRepository
) {
    /**
     * Try to find an [InfrastructureService] defined for the given [organization][organizationId] and
     * [product][productId] that matches the given [repositoryUrl]. This function is used to find the credentials for
     * downloading the repository. The match is done based on the URL prefix. In case there are multiple matches, the
     * longest match wins.
     */
    fun findInfrastructureServiceForRepository(
        repositoryUrl: String,
        organizationId: Long,
        productId: Long
    ): InfrastructureService? =
        infrastructureServiceRepository.listForRepositoryUrl(repositoryUrl, organizationId, productId)
            .filter { repositoryUrl.startsWith(it.url) }
            .maxByOrNull { it.url.length }
}
