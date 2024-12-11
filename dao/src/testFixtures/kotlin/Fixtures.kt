/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.test

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.DaoAdvisorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorrun.DaoAdvisorRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.DaoAnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerrun.DaoAnalyzerRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.DaoEvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorrun.DaoEvaluatorRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.infrastructureservice.DaoInfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob.DaoNotifierJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierrun.DaoNotifierRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.DaoOrganizationRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.product.DaoProductRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.DaoReporterJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterrun.DaoReporterRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.DaoRepositoryRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration.DaoRepositoryConfigurationRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.resolvedconfiguration.DaoResolvedConfigurationRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.DaoScannerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerrun.DaoScannerRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.DaoSecretRepository
import org.eclipse.apoapsis.ortserver.dao.tables.shared.IdentifierDao
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JiraNotificationConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.Jobs
import org.eclipse.apoapsis.ortserver.model.MailNotificationConfiguration
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.model.PluginConfiguration
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Environment
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.OrtRuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorResult

import org.jetbrains.exposed.sql.Database

/**
 * A helper class to manage test fixtures. It provides default instances as well as helper functions to create custom
 * instances.
 */
class Fixtures(private val db: Database) {
    val advisorJobRepository = DaoAdvisorJobRepository(db)
    val advisorRunRepository = DaoAdvisorRunRepository(db)
    val analyzerJobRepository = DaoAnalyzerJobRepository(db)
    val analyzerRunRepository = DaoAnalyzerRunRepository(db)
    val evaluatorJobRepository = DaoEvaluatorJobRepository(db)
    val evaluatorRunRepository = DaoEvaluatorRunRepository(db)
    val infrastructureServiceRepository = DaoInfrastructureServiceRepository(db)
    val organizationRepository = DaoOrganizationRepository(db)
    val ortRunRepository = DaoOrtRunRepository(db)
    val productRepository = DaoProductRepository(db)
    val repositoryConfigurationRepository = DaoRepositoryConfigurationRepository(db)
    val reporterJobRepository = DaoReporterJobRepository(db)
    val reporterRunRepository = DaoReporterRunRepository(db)
    val notifierJobRepository = DaoNotifierJobRepository(db)
    val notifierRunRepository = DaoNotifierRunRepository(db)
    val repositoryRepository = DaoRepositoryRepository(db)
    val resolvedConfigurationRepository = DaoResolvedConfigurationRepository(db)
    val scannerJobRepository = DaoScannerJobRepository(db)
    val scannerRunRepository = DaoScannerRunRepository(db)
    val secretRepository = DaoSecretRepository(db)

    val organization by lazy { createOrganization() }
    val product by lazy { createProduct() }
    val repository by lazy { createRepository() }
    val ortRun by lazy { createOrtRun() }
    val analyzerJob by lazy { createAnalyzerJob() }
    val advisorJob by lazy { createAdvisorJob() }
    val scannerJob by lazy { createScannerJob() }
    val evaluatorJob by lazy { createEvaluatorJob() }
    val reporterJob by lazy { createReporterJob() }
    val notifierJob by lazy { createNotifierJob() }
    val identifier by lazy { createIdentifier() }
    val ruleViolation by lazy { getViolation() }

    val jobConfigurations = JobConfigurations(
        analyzer = AnalyzerJobConfiguration(
            allowDynamicVersions = true
        ),
        advisor = AdvisorJobConfiguration(
            advisors = listOf("OSV")
        ),
        scanner = ScannerJobConfiguration(),
        evaluator = EvaluatorJobConfiguration(
            ruleSet = "default"
        ),
        reporter = ReporterJobConfiguration(
            formats = listOf("WebApp")
        ),
        notifier = NotifierJobConfiguration(
            mail = MailNotificationConfiguration(
                recipientAddresses = listOf("test@example.com")
            ),
            jira = JiraNotificationConfiguration()
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
    ) = ortRunRepository.create(
        repositoryId,
        revision,
        null,
        jobConfigurations,
        null,
        mapOf("label key" to "label value"),
        traceId = "trace-this-run0",
        environmentConfigPath = "path/to/env.yml"
    )

    fun createAnalyzerJob(
        ortRunId: Long = ortRun.id,
        configuration: AnalyzerJobConfiguration = jobConfigurations.analyzer
    ) = analyzerJobRepository.create(ortRunId, configuration)

    fun createAdvisorJob(
        ortRunId: Long = ortRun.id,
        configuration: AdvisorJobConfiguration = checkNotNull(jobConfigurations.advisor)
    ) = advisorJobRepository.create(ortRunId, configuration)

    fun createScannerJob(
        ortRunId: Long = ortRun.id,
        configuration: ScannerJobConfiguration = checkNotNull(jobConfigurations.scanner)
    ) = scannerJobRepository.create(ortRunId, configuration)

    fun createEvaluatorJob(
        ortRunId: Long = ortRun.id,
        configuration: EvaluatorJobConfiguration = checkNotNull(jobConfigurations.evaluator)
    ) = evaluatorJobRepository.create(ortRunId, configuration)

    fun createReporterJob(
        ortRunId: Long = ortRun.id,
        configuration: ReporterJobConfiguration = checkNotNull(jobConfigurations.reporter)
    ) = reporterJobRepository.create(ortRunId, configuration)

    fun createNotifierJob(
        ortRunId: Long = ortRun.id,
        configuration: NotifierJobConfiguration = checkNotNull(jobConfigurations.notifier)
    ) = notifierJobRepository.create(ortRunId, configuration)

    fun createJobs(ortRunId: Long): Jobs {
        val analyzerJob = createAnalyzerJob(ortRunId)
        val advisorJob = createAdvisorJob(ortRunId)
        val scannerJob = createScannerJob(ortRunId)
        val evaluatorJob = createEvaluatorJob(ortRunId)
        val reporterJob = createReporterJob(ortRunId)
        val notifierJob = createNotifierJob(ortRunId)

        return Jobs(analyzerJob, advisorJob, scannerJob, evaluatorJob, reporterJob, notifierJob)
    }

    fun createIdentifier(
        identifier: Identifier = Identifier(
            "identifier_type",
            "identifier_namespace",
            "identifier_package",
            "identifier_version"
        )
    ): Identifier = db.blockingQuery {
        IdentifierDao.getOrPut(identifier).mapToModel()
    }

    fun getViolation() = OrtRuleViolation(
        rule = "rule",
        packageId = identifier,
        license = "license",
        licenseSource = "license source",
        message = "message",
        severity = Severity.ERROR,
        howToFix = "how to fix"
    )

    fun createAdvisorRun(
        advisorJobId: Long = advisorJob.id,
        results: Map<Identifier, List<AdvisorResult>>
    ) = advisorRunRepository.create(
        advisorJobId = advisorJobId,
        startTime = Clock.System.now(),
        endTime = Clock.System.now(),
        environment = Environment(
            ortVersion = "1.0",
            javaVersion = "11.0.16",
            os = "Linux",
            processors = 8,
            maxMemory = 8321499136,
            variables = emptyMap(),
            toolVersions = emptyMap()
        ),
        config = AdvisorConfiguration(
            config = mapOf(
                "VulnerableCode" to PluginConfiguration(
                    options = mapOf("serverUrl" to "https://public.vulnerablecode.io"),
                    secrets = mapOf("apiKey" to "key")
                )
            )
        ),
        results = results
    )
}
