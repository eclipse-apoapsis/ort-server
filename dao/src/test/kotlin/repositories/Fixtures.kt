/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.test.repositories

import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrganizationRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrtRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoProductRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.RepositoryType

/**
 * A helper class to manage test fixtures. It provides default instances as well as helper functions to create custom
 * instances.
 */
class Fixtures {
    private val organizationRepository = DaoOrganizationRepository()
    private val productRepository = DaoProductRepository()
    private val repositoryRepository = DaoRepositoryRepository()
    private val ortRunRepository = DaoOrtRunRepository()
    private val analyzerJobRepository = DaoAnalyzerJobRepository()

    val organization by lazy { createOrganization() }
    val product by lazy { createProduct() }
    val repository by lazy { createRepository() }
    val ortRun by lazy { createOrtRun() }
    val analyzerJob by lazy { createAnalyzerJob() }

    val jobConfigurations = JobConfigurations(
        analyzer = AnalyzerJobConfiguration(
            allowDynamicVersions = true
        )
    )

    fun createOrganization(name: String = "name", description: String = "description") =
        organizationRepository.create(name, description)

    fun createProduct(
        name: String = "name",
        description: String = "description",
        organizationId: Long = organization.id
    ) = productRepository.create(name, description, organizationId)

    fun createRepository(
        type: RepositoryType = RepositoryType.GIT,
        url: String = "https://example.com/repo.git",
        productId: Long = product.id
    ) = repositoryRepository.create(type, url, productId)

    fun createOrtRun(
        repositoryId: Long = repository.id,
        revision: String = "revision",
        jobConfigurations: JobConfigurations = this.jobConfigurations
    ) = ortRunRepository.create(repositoryId, revision, jobConfigurations)

    fun createAnalyzerJob(
        ortRunId: Long = ortRun.id,
        configuration: AnalyzerJobConfiguration = jobConfigurations.analyzer
    ) = analyzerJobRepository.create(ortRunId, configuration)
}
