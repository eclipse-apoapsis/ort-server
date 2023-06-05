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

package org.ossreviewtoolkit.server.dao.test

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoEvaluatorJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrganizationRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrtRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoProductRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoReporterJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoScannerJobRepository
import org.ossreviewtoolkit.server.dao.tables.runs.shared.IdentifierDao
import org.ossreviewtoolkit.server.model.AdvisorJobConfiguration
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.ScannerJobConfiguration
import org.ossreviewtoolkit.server.model.runs.OrtRuleViolation

/**
 * A helper class to manage test fixtures. It provides default instances as well as helper functions to create custom
 * instances.
 */
class Fixtures(private val db: Database) {
    val advisorJobRepository = DaoAdvisorJobRepository(db)
    val analyzerJobRepository = DaoAnalyzerJobRepository(db)
    val evaluatorJobRepository = DaoEvaluatorJobRepository(db)
    val organizationRepository = DaoOrganizationRepository(db)
    val ortRunRepository = DaoOrtRunRepository(db)
    val productRepository = DaoProductRepository(db)
    val reporterJobRepository = DaoReporterJobRepository(db)
    val repositoryRepository = DaoRepositoryRepository(db)
    val scannerJobRepository = DaoScannerJobRepository(db)

    val organization by lazy { createOrganization() }
    val product by lazy { createProduct() }
    val repository by lazy { createRepository() }
    val ortRun by lazy { createOrtRun() }
    val analyzerJob by lazy { createAnalyzerJob() }
    val advisorJob by lazy { createAdvisorJob() }
    val scannerJob by lazy { createScannerJob() }
    val evaluatorJob by lazy { createEvaluatorJob() }
    val reporterJob by lazy { createReporterJob() }
    val identifier by lazy { createIdentifier() }
    val ruleViolation by lazy { getViolation() }

    val jobConfigurations = JobConfigurations(
        analyzer = AnalyzerJobConfiguration(
            allowDynamicVersions = true
        ),
        advisor = AdvisorJobConfiguration(
            advisors = listOf("OSV")
        ),
        scanner = ScannerJobConfiguration(
            false
        ),
        evaluator = EvaluatorJobConfiguration(
            ruleSet = "default"
        ),
        reporter = ReporterJobConfiguration(
            formats = listOf("WebApp")
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

    fun createAdvisorJob(
        ortRunId: Long = ortRun.id,
        configuration: AdvisorJobConfiguration = jobConfigurations.advisor!!
    ) = advisorJobRepository.create(ortRunId, configuration)

    fun createScannerJob(
        ortRunId: Long = ortRun.id,
        configuration: ScannerJobConfiguration = jobConfigurations.scanner!!
    ) = scannerJobRepository.create(ortRunId, configuration)

    fun createEvaluatorJob(
        ortRunId: Long = ortRun.id,
        configuration: EvaluatorJobConfiguration = jobConfigurations.evaluator!!
    ) = evaluatorJobRepository.create(ortRunId, configuration)

    fun createReporterJob(
        ortRunId: Long = ortRun.id,
        configuration: ReporterJobConfiguration = jobConfigurations.reporter!!
    ) = reporterJobRepository.create(ortRunId, configuration)

    fun createIdentifier() = db.blockingQuery {
        IdentifierDao.new {
            type = "identifier_type"
            namespace = "identifier_namespace"
            name = "identifier_package"
            version = "identifier_version"
        }.mapToModel()
    }

    fun getViolation() = OrtRuleViolation(
        rule = "rule",
        packageId = identifier,
        license = "license",
        licenseSource = "license source",
        message = "message",
        severity = "severity",
        howToFix = "how to fix"
    )
}
