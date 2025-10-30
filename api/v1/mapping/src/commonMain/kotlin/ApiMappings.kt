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

@file:Suppress("TooManyFunctions")

package org.eclipse.apoapsis.ortserver.api.v1.mapping

import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorCapability as ApiAdvisorCapability
import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorDetails as ApiAdvisorDetails
import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJob as ApiAdvisorJob
import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJobConfiguration as ApiAdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJob as ApiAnalyzerJob
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJobConfiguration as ApiAnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.ComparisonOperator as ApiComparisonOperator
import org.eclipse.apoapsis.ortserver.api.v1.model.ContentManagementSection as ApiContentManagementSection
import org.eclipse.apoapsis.ortserver.api.v1.model.EcosystemStats as ApiEcosystemStats
import org.eclipse.apoapsis.ortserver.api.v1.model.EnvironmentConfig as ApiEnvironmentConfig
import org.eclipse.apoapsis.ortserver.api.v1.model.EnvironmentVariableDeclaration as ApiEnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.api.v1.model.EvaluatorJob as ApiEvaluatorJob
import org.eclipse.apoapsis.ortserver.api.v1.model.EvaluatorJobConfiguration as ApiEvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.FilterOperatorAndValue as ApiFilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier as ApiIdentifier
import org.eclipse.apoapsis.ortserver.api.v1.model.Issue as ApiIssue
import org.eclipse.apoapsis.ortserver.api.v1.model.IssueResolution as ApiIssueResolution
import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations as ApiJobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.JobStatus as ApiJobStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummaries as ApiJobSummaries
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummary as ApiJobSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs as ApiJobs
import org.eclipse.apoapsis.ortserver.api.v1.model.LogLevel as ApiLogLevel
import org.eclipse.apoapsis.ortserver.api.v1.model.LogSource as ApiLogSource
import org.eclipse.apoapsis.ortserver.api.v1.model.NotifierJob as ApiNotifierJob
import org.eclipse.apoapsis.ortserver.api.v1.model.NotifierJobConfiguration as ApiNotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.OidcConfig as ApiOidcConfig
import org.eclipse.apoapsis.ortserver.api.v1.model.Organization as ApiOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun as ApiOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunFilters as ApiOrtRunFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus as ApiOrtRunStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunSummary as ApiOrtRunSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.Package as ApiPackage
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageCuration
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageCurationData as ApiPackageCurationData
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageFilters as ApiPackageFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageManagerConfiguration as ApiPackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.PluginConfig as ApiPluginConfig
import org.eclipse.apoapsis.ortserver.api.v1.model.ProcessedDeclaredLicense as ApiProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.api.v1.model.Product as ApiProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.ProductVulnerability as ApiProductVulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.Project as ApiProject
import org.eclipse.apoapsis.ortserver.api.v1.model.ProviderPluginConfiguration as ApiProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.RemoteArtifact as ApiRemoteArtifact
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterJob as ApiReporterJob
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterJobConfiguration as ApiReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.Repository as ApiRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType as ApiRepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.RuleViolation as ApiRuleViolation
import org.eclipse.apoapsis.ortserver.api.v1.model.RuleViolationFilters as ApiRuleViolationFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.RuleViolationResolution as ApiRuleViolationResolution
import org.eclipse.apoapsis.ortserver.api.v1.model.ScannerJob as ApiScannerJob
import org.eclipse.apoapsis.ortserver.api.v1.model.ScannerJobConfiguration as ApiScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.Severity as ApiSeverity
import org.eclipse.apoapsis.ortserver.api.v1.model.ShortestDependencyPath as ApiShortestDependencyPath
import org.eclipse.apoapsis.ortserver.api.v1.model.SourceCodeOrigin as ApiSourceCodeOrigin
import org.eclipse.apoapsis.ortserver.api.v1.model.SubmoduleFetchStrategy as ApiSubmoduleFetchStrategy
import org.eclipse.apoapsis.ortserver.api.v1.model.User as ApiUser
import org.eclipse.apoapsis.ortserver.api.v1.model.UserGroup as ApiUserGroup
import org.eclipse.apoapsis.ortserver.api.v1.model.VcsInfo as ApiVcsInfo
import org.eclipse.apoapsis.ortserver.api.v1.model.VcsInfoCurationData as ApiVcsInfoCurationData
import org.eclipse.apoapsis.ortserver.api.v1.model.Vulnerability as ApiVulnerability
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityFilters as ApiVulnerabilityFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityForRunsFilters as ApiVulnerabilityForRunsFilters
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityRating as ApiVulnerabilityRating
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityReference as ApiVulnerabilityReference
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityResolution as ApiVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.api.v1.model.VulnerabilityWithDetails as ApiVulnerabilityWithDetails
import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AppliedVulnerabilityResolution
import org.eclipse.apoapsis.ortserver.model.ContentManagementSection
import org.eclipse.apoapsis.ortserver.model.EcosystemStats
import org.eclipse.apoapsis.ortserver.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.model.EnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.JobSummaries
import org.eclipse.apoapsis.ortserver.model.JobSummary
import org.eclipse.apoapsis.ortserver.model.Jobs
import org.eclipse.apoapsis.ortserver.model.LogLevel
import org.eclipse.apoapsis.ortserver.model.LogSource
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunFilters
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.OrtRunSummary
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.ResolvablePluginConfig
import org.eclipse.apoapsis.ortserver.model.ResolvableProviderPluginConfig
import org.eclipse.apoapsis.ortserver.model.ResolvableSecret
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.SecretSource
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.SourceCodeOrigin
import org.eclipse.apoapsis.ortserver.model.SubmoduleFetchStrategy
import org.eclipse.apoapsis.ortserver.model.User
import org.eclipse.apoapsis.ortserver.model.UserGroup
import org.eclipse.apoapsis.ortserver.model.VulnerabilityFilters
import org.eclipse.apoapsis.ortserver.model.VulnerabilityForRunsFilters
import org.eclipse.apoapsis.ortserver.model.VulnerabilityRating
import org.eclipse.apoapsis.ortserver.model.VulnerabilityWithAccumulatedData
import org.eclipse.apoapsis.ortserver.model.VulnerabilityWithDetails
import org.eclipse.apoapsis.ortserver.model.authentication.OidcConfig
import org.eclipse.apoapsis.ortserver.model.runs.Identifier
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.Package
import org.eclipse.apoapsis.ortserver.model.runs.PackageFilters
import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.model.runs.ProcessedDeclaredLicense
import org.eclipse.apoapsis.ortserver.model.runs.Project
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolation
import org.eclipse.apoapsis.ortserver.model.runs.RuleViolationFilters
import org.eclipse.apoapsis.ortserver.model.runs.ShortestDependencyPath
import org.eclipse.apoapsis.ortserver.model.runs.VcsInfo
import org.eclipse.apoapsis.ortserver.model.runs.advisor.AdvisorDetails
import org.eclipse.apoapsis.ortserver.model.runs.advisor.Vulnerability
import org.eclipse.apoapsis.ortserver.model.runs.advisor.VulnerabilityReference
import org.eclipse.apoapsis.ortserver.model.runs.repository.IssueResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.PackageCurationData
import org.eclipse.apoapsis.ortserver.model.runs.repository.RuleViolationResolution
import org.eclipse.apoapsis.ortserver.model.runs.repository.VcsInfoCurationData
import org.eclipse.apoapsis.ortserver.model.runs.repository.VulnerabilityResolution
import org.eclipse.apoapsis.ortserver.model.util.ComparisonOperator
import org.eclipse.apoapsis.ortserver.model.util.FilterOperatorAndValue
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToApi
import org.eclipse.apoapsis.ortserver.shared.apimappings.mapToModel

fun AdvisorJob.mapToApi() =
    ApiAdvisorJob(
        id = id,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        configuration = configuration.mapToApi(),
        status = status.mapToApi()
    )

fun AdvisorJob.mapToApiSummary() =
    ApiJobSummary(
        id = id,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status.mapToApi()
    )

fun AdvisorJobConfiguration.mapToApi() =
    ApiAdvisorJobConfiguration(
        advisors = advisors,
        skipExcluded = skipExcluded,
        config = config?.mapValues { it.value.mapToApi() },
        keepAliveWorker = keepAliveWorker
    )

fun ApiAdvisorJobConfiguration.mapToModel() =
    AdvisorJobConfiguration(
        advisors = advisors,
        skipExcluded = skipExcluded,
        config = config?.mapValues { it.value.mapToModel() },
        keepAliveWorker = keepAliveWorker
    )

fun AnalyzerJob.mapToApi() =
    ApiAnalyzerJob(
        id = id,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        configuration = configuration.mapToApi(),
        status = status.mapToApi()
    )

fun AnalyzerJob.mapToApiSummary() =
    ApiJobSummary(
        id = id,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status.mapToApi()
    )

fun AnalyzerJobConfiguration.mapToApi() =
    ApiAnalyzerJobConfiguration(
        allowDynamicVersions = allowDynamicVersions,
        disabledPackageManagers = disabledPackageManagers,
        enabledPackageManagers = enabledPackageManagers,
        environmentConfig = environmentConfig?.mapToApi(),
        submoduleFetchStrategy = submoduleFetchStrategy?.mapToApi(),
        packageCurationProviders = packageCurationProviders.map { it.mapToApi() },
        packageManagerOptions = packageManagerOptions?.mapValues { it.value.mapToApi() },
        repositoryConfigPath = repositoryConfigPath,
        skipExcluded = skipExcluded,
        keepAliveWorker = keepAliveWorker
    )

fun ApiAnalyzerJobConfiguration.mapToModel() =
    AnalyzerJobConfiguration(
        allowDynamicVersions = allowDynamicVersions,
        disabledPackageManagers = disabledPackageManagers,
        enabledPackageManagers = enabledPackageManagers,
        environmentConfig = environmentConfig?.mapToModel(),
        submoduleFetchStrategy = submoduleFetchStrategy?.mapToModel(),
        packageCurationProviders = packageCurationProviders?.map { it.mapToModel() }.orEmpty(),
        packageManagerOptions = packageManagerOptions?.mapValues { it.value.mapToModel() },
        repositoryConfigPath = repositoryConfigPath,
        skipExcluded = skipExcluded,
        keepAliveWorker = keepAliveWorker
    )

fun EvaluatorJob.mapToApi() =
    ApiEvaluatorJob(
        id = id,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        configuration = configuration.mapToApi(),
        status = status.mapToApi()
    )

fun EvaluatorJob.mapToApiSummary() =
    ApiJobSummary(
        id = id,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status.mapToApi()
    )

fun EvaluatorJobConfiguration.mapToApi() =
    ApiEvaluatorJobConfiguration(
        packageConfigurationProviders = packageConfigurationProviders.map { it.mapToApi() },
        keepAliveWorker = keepAliveWorker
    )

fun ApiEvaluatorJobConfiguration.mapToModel() =
    EvaluatorJobConfiguration(
        packageConfigurationProviders = packageConfigurationProviders?.map { it.mapToModel() }.orEmpty(),
        keepAliveWorker = keepAliveWorker
    )

fun Issue.mapToApi() =
    ApiIssue(
        timestamp = timestamp,
        source = source,
        message = message,
        severity = severity.mapToApi(),
        affectedPath = affectedPath,
        identifier = identifier?.mapToApi(),
        worker = worker,
        resolutions = resolutions.map { it.mapToApi() },
        purl = purl
    )

fun ApiIssue.mapToModel() =
    Issue(
        timestamp = timestamp,
        source = source,
        message = message,
        severity = severity.mapToModel(),
        affectedPath = affectedPath,
        identifier = identifier?.mapToModel(),
        worker = worker,
        resolutions = resolutions.map { it.mapToModel() }
    )

fun IssueResolution.mapToApi() = ApiIssueResolution(message = message, reason = reason, comment = comment)

fun ApiIssueResolution.mapToModel() =
    IssueResolution(message = message, reason = reason, comment = comment)

fun Severity.mapToApi() = when (this) {
    Severity.ERROR -> ApiSeverity.ERROR
    Severity.WARNING -> ApiSeverity.WARNING
    Severity.HINT -> ApiSeverity.HINT
}

fun ApiSeverity.mapToModel() = when (this) {
    ApiSeverity.ERROR -> Severity.ERROR
    ApiSeverity.WARNING -> Severity.WARNING
    ApiSeverity.HINT -> Severity.HINT
}

fun JobStatus.mapToApi() = ApiJobStatus.valueOf(name)

fun ApiJobStatus.mapToModel() = JobStatus.valueOf(name)

fun JobConfigurations.mapToApi() =
    ApiJobConfigurations(
        analyzer = analyzer.mapToApi(),
        advisor = advisor?.mapToApi(),
        scanner = scanner?.mapToApi(),
        evaluator = evaluator?.mapToApi(),
        reporter = reporter?.mapToApi(),
        notifier = notifier?.mapToApi(),
        parameters = parameters,
        ruleSet = ruleSet
    )

fun ApiJobConfigurations.mapToModel() =
    JobConfigurations(
        analyzer = analyzer.mapToModel(),
        advisor = advisor?.mapToModel(),
        scanner = scanner?.mapToModel(),
        evaluator = evaluator?.mapToModel(),
        reporter = reporter?.mapToModel(),
        notifier = notifier?.mapToModel(),
        parameters = parameters.orEmpty(),
        ruleSet = ruleSet
    )

fun Jobs.mapToApi() =
    ApiJobs(
        analyzer = analyzer?.mapToApi(),
        advisor = advisor?.mapToApi(),
        scanner = scanner?.mapToApi(),
        evaluator = evaluator?.mapToApi(),
        reporter = reporter?.mapToApi(),
        notifier = notifier?.mapToApi()
    )

fun Jobs.mapToApiSummary() =
    ApiJobSummaries(
        analyzer = analyzer?.mapToApiSummary(),
        advisor = advisor?.mapToApiSummary(),
        scanner = scanner?.mapToApiSummary(),
        evaluator = evaluator?.mapToApiSummary(),
        reporter = reporter?.mapToApiSummary()
    )

fun LogLevel.mapToApi() = ApiLogLevel.valueOf(name)

fun ApiLogLevel.mapToModel() = LogLevel.valueOf(name)

fun LogSource.mapToApi() = ApiLogSource.valueOf(name)

fun ApiLogSource.mapToModel() = LogSource.valueOf(name)

fun Organization.mapToApi() = ApiOrganization(id, name, description)

fun OrtRun.mapToApi(jobs: ApiJobs) =
    ApiOrtRun(
        id = id,
        index = index,
        organizationId = organizationId,
        productId = productId,
        repositoryId = repositoryId,
        revision = revision,
        resolvedRevision = resolvedRevision,
        path = path,
        createdAt = createdAt,
        finishedAt = finishedAt,
        jobConfigs = jobConfigs.mapToApi(),
        resolvedJobConfigs = resolvedJobConfigs?.mapToApi(),
        jobs = jobs,
        status = status.mapToApi(),
        labels = labels,
        issues = issues.map { it.mapToApi() },
        jobConfigContext = jobConfigContext,
        resolvedJobConfigContext = resolvedJobConfigContext,
        environmentConfigPath = environmentConfigPath,
        traceId = traceId,
        userDisplayName = userDisplayName?.mapToApi(),
        outdated = outdated,
        outdatedMessage = outdatedMessage
    )

fun OrtRun.mapToApiSummary(jobs: ApiJobSummaries) =
    ApiOrtRunSummary(
        id = id,
        index = index,
        organizationId = organizationId,
        productId = productId,
        repositoryId = repositoryId,
        revision = revision,
        resolvedRevision = resolvedRevision,
        path = path,
        createdAt = createdAt,
        finishedAt = finishedAt,
        jobs = jobs,
        status = status.mapToApi(),
        labels = labels,
        jobConfigContext = jobConfigContext,
        resolvedJobConfigContext = resolvedJobConfigContext,
        environmentConfigPath = environmentConfigPath,
        userDisplayName = userDisplayName?.mapToApi(),
        outdated = outdated,
        outdatedMessage = outdatedMessage
    )

fun OrtRunSummary.mapToApi() =
    ApiOrtRunSummary(
        id = id,
        index = index,
        organizationId = organizationId,
        productId = productId,
        repositoryId = repositoryId,
        revision = revision,
        resolvedRevision = resolvedRevision,
        path = path,
        createdAt = createdAt,
        finishedAt = finishedAt,
        jobs = jobs.mapToApi(),
        status = status.mapToApi(),
        labels = labels,
        jobConfigContext = jobConfigContext,
        resolvedJobConfigContext = resolvedJobConfigContext,
        environmentConfigPath = environmentConfigPath,
        userDisplayName = userDisplayName?.mapToApi()
    )

fun JobSummaries.mapToApi() =
    ApiJobSummaries(
        analyzer = analyzer?.mapToApi(),
        advisor = advisor?.mapToApi(),
        scanner = scanner?.mapToApi(),
        evaluator = evaluator?.mapToApi(),
        reporter = reporter?.mapToApi()
    )

fun JobSummary.mapToApi() = ApiJobSummary(
    id = id,
    createdAt = createdAt,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = status.mapToApi()
)

fun OrtRunStatus.mapToApi() = ApiOrtRunStatus.valueOf(name)

fun ApiOrtRunStatus.mapToModel() = OrtRunStatus.valueOf(name)

fun <T, E> ApiFilterOperatorAndValue<T>.mapToModel(mapValues: (T) -> E): FilterOperatorAndValue<E> =
    FilterOperatorAndValue(
        operator = operator.mapToModel(),
        value = mapValues(value)
    )

fun ApiOrtRunFilters.mapToModel(): OrtRunFilters =
    OrtRunFilters(
        status = status?.mapToModel { statusSet: Set<ApiOrtRunStatus> ->
            statusSet.map { it.mapToModel() }.toSet()
        }
    )

fun ApiComparisonOperator.mapToModel() = ComparisonOperator.valueOf(name)

fun Product.mapToApi() = ApiProduct(id = id, organizationId = organizationId, name = name, description = description)

fun Repository.mapToApi() = ApiRepository(
    id = id,
    organizationId = organizationId,
    productId = productId,
    type = type.mapToApi(),
    url = url,
    description = description
)

fun RepositoryType.mapToApi() = ApiRepositoryType.valueOf(name)

fun ApiRepositoryType.mapToModel() = RepositoryType.forName(name)

fun ReporterJob.mapToApi() =
    ApiReporterJob(
        id = id,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        configuration = configuration.mapToApi(),
        status = status.mapToApi(),
        reportFilenames = filenames
    )

fun ReporterJob.mapToApiSummary() =
    ApiJobSummary(
        id = id,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status.mapToApi()
    )

fun ReporterJobConfiguration.mapToApi() =
    ApiReporterJobConfiguration(
        formats = formats,
        assetFilesGroups = assetFilesGroups,
        assetDirectoriesGroups = assetDirectoriesGroups,
        packageConfigurationProviders = packageConfigurationProviders.map { it.mapToApi() },
        config = config?.mapValues { it.value.mapToApi() },
        keepAliveWorker = keepAliveWorker
    )

fun NotifierJob.mapToApi() =
    ApiNotifierJob(
        id = id,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        configuration = configuration.mapToApi(),
        status = status.mapToApi()
    )

fun NotifierJobConfiguration.mapToApi() =
    ApiNotifierJobConfiguration(
        recipientAddresses = recipientAddresses,
        keepAliveWorker = keepAliveWorker
    )

fun ApiNotifierJobConfiguration.mapToModel() =
    NotifierJobConfiguration(
        recipientAddresses = recipientAddresses,
        keepAliveWorker = keepAliveWorker
    )

fun ApiReporterJobConfiguration.mapToModel() =
    ReporterJobConfiguration(
        formats = formats,
        assetFilesGroups = assetFilesGroups,
        assetDirectoriesGroups = assetDirectoriesGroups,
        packageConfigurationProviders = packageConfigurationProviders?.map { it.mapToModel() }.orEmpty(),
        config = config?.mapValues { it.value.mapToModel() },
        keepAliveWorker = keepAliveWorker
    )

fun ScannerJob.mapToApi() =
    ApiScannerJob(
        id = id,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        configuration = configuration.mapToApi(),
        status = status.mapToApi()
    )

fun ScannerJob.mapToApiSummary() =
    ApiJobSummary(
        id = id,
        createdAt = createdAt,
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status.mapToApi()
    )

fun ScannerJobConfiguration.mapToApi() = ApiScannerJobConfiguration(
    projectScanners = projectScanners,
    scanners = scanners,
    skipConcluded = skipConcluded,
    skipExcluded = skipExcluded,
    config = config?.mapValues { it.value.mapToApi() },
    keepAliveWorker = keepAliveWorker,
    submoduleFetchStrategy = submoduleFetchStrategy.mapToApi()
)

fun ApiScannerJobConfiguration.mapToModel() = ScannerJobConfiguration(
    projectScanners = projectScanners,
    scanners = scanners,
    skipConcluded = skipConcluded,
    skipExcluded = skipExcluded,
    config = config?.mapValues { it.value.mapToModel() },
    keepAliveWorker = keepAliveWorker,
    submoduleFetchStrategy = submoduleFetchStrategy.mapToModel()
)

fun AdvisorDetails.mapToApi() = ApiAdvisorDetails(
    name = name,
    capabilities = capabilities.map { ApiAdvisorCapability.valueOf(it.name) }.toSet()
)

fun AppliedVulnerabilityResolution.mapToApi() =
    ApiVulnerabilityResolution(
        externalId = resolution.externalId,
        reason = resolution.reason,
        comment = resolution.comment,
        definition = definition?.mapToApi()
    )

fun VulnerabilityWithDetails.mapToApi() =
    ApiVulnerabilityWithDetails(
        vulnerability = vulnerability.mapToApi(),
        identifier = identifier.mapToApi(),
        rating = rating.mapToApi(),
        resolutions = resolutions.map { it.mapToApi() },
        newMatchingResolutionDefinitions = newMatchingResolutionDefinitions.map { it.mapToApi() },
        advisor = advisor.mapToApi(),
        purl = purl
    )

fun Vulnerability.mapToApi() = ApiVulnerability(
    externalId = externalId,
    summary = summary,
    description = description,
    references = references.map { it.mapToApi() }
)

fun RuleViolation.mapToApi() = ApiRuleViolation(
    rule = rule,
    id = id?.mapToApi(),
    license = license,
    // TODO: Add support for multiple license sources, see issue #4185.
    licenseSource = licenseSources.firstOrNull(),
    severity = severity.mapToApi(),
    message = message,
    howToFix = howToFix,
    resolutions = resolutions.map { it.mapToApi() },
    purl = purl
)

fun Identifier.mapToApi() = ApiIdentifier(type = type, namespace = namespace, name = name, version = version)

fun ApiIdentifier.mapToModel() = Identifier(type = type, namespace = namespace, name = name, version = version)

fun VulnerabilityReference.mapToApi() = ApiVulnerabilityReference(
    url = url,
    scoringSystem = scoringSystem,
    severity = severity,
    score = score,
    vector = vector
)

fun ProcessedDeclaredLicense.mapToApi() =
    ApiProcessedDeclaredLicense(
        spdxExpression = spdxExpression,
        mappedLicenses = mappedLicenses,
        unmappedLicenses = unmappedLicenses
    )

fun RemoteArtifact.mapToApi() =
    ApiRemoteArtifact(
        url = url,
        hashValue = hashValue,
        hashAlgorithm = hashAlgorithm
    )

fun VcsInfo.mapToApi() =
    ApiVcsInfo(
        type = type.name,
        url = url,
        revision = revision,
        path = path
    )

fun ApiEnvironmentVariableDeclaration.mapToModel() = EnvironmentVariableDeclaration(
    name = name,
    secretName = secretName,
    value = value
)

fun EnvironmentVariableDeclaration.mapToApi() = ApiEnvironmentVariableDeclaration(
    name = name,
    secretName = secretName,
    value = value
)

fun EnvironmentConfig.mapToApi() =
    ApiEnvironmentConfig(
        infrastructureServices = infrastructureServices.map { it.mapToApi() },
        environmentDefinitions = environmentDefinitions,
        environmentVariables = environmentVariables.map { it.mapToApi() },
        strict = strict
    )

fun ApiEnvironmentConfig.mapToModel() =
    EnvironmentConfig(
        infrastructureServices = infrastructureServices.map { it.mapToModel() },
        environmentDefinitions = environmentDefinitions,
        environmentVariables = environmentVariables.map { it.mapToModel() },
        strict = strict
    )

fun PackageManagerConfiguration.mapToApi() =
    ApiPackageManagerConfiguration(mustRunAfter = mustRunAfter, options = options)

fun ApiPackageManagerConfiguration.mapToModel() =
    PackageManagerConfiguration(mustRunAfter = mustRunAfter, options = options)

fun ResolvablePluginConfig.mapToApi() = ApiPluginConfig(
    options = options,
    secrets = secrets.mapValues { it.value.name }
)

fun ApiPluginConfig.mapToModel() = ResolvablePluginConfig(
    options = options,
    secrets = secrets.mapValues { ResolvableSecret(it.value, SecretSource.ADMIN) }
)

fun ResolvableProviderPluginConfig.mapToApi() =
    ApiProviderPluginConfiguration(
        type = type,
        id = id,
        enabled = enabled,
        options = options,
        secrets = secrets.mapValues { it.value.name }
    )

fun ApiProviderPluginConfiguration.mapToModel() =
    ResolvableProviderPluginConfig(
        type = type,
        id = id,
        enabled = enabled,
        options = options,
        secrets = secrets.mapValues { ResolvableSecret(it.value, SecretSource.ADMIN) }
    )

fun SourceCodeOrigin.mapToApi() =
    when (this) {
        SourceCodeOrigin.ARTIFACT -> ApiSourceCodeOrigin.ARTIFACT
        SourceCodeOrigin.VCS -> ApiSourceCodeOrigin.VCS
    }

fun ApiSourceCodeOrigin.mapToModel() =
    when (this) {
        ApiSourceCodeOrigin.ARTIFACT -> SourceCodeOrigin.ARTIFACT
        ApiSourceCodeOrigin.VCS -> SourceCodeOrigin.VCS
    }

fun User.mapToApi() = ApiUser(username = username, firstName = firstName, lastName = lastName, email = email)

fun UserGroup.mapToApi() = ApiUserGroup.valueOf(name)

fun EcosystemStats.mapToApi() = ApiEcosystemStats(name = name, count = count)

fun VulnerabilityRating.mapToApi() = ApiVulnerabilityRating.valueOf(name)

fun VulnerabilityWithAccumulatedData.mapToApi() = ApiProductVulnerability(
    vulnerability = vulnerability.mapToApi(),
    identifier = identifier.mapToApi(),
    purl = purl,
    rating = rating.mapToApi(),
    ortRunIds = ortRunIds,
    repositoriesCount = repositoriesCount
)

fun SubmoduleFetchStrategy.mapToApi() = when (this) {
    SubmoduleFetchStrategy.DISABLED -> ApiSubmoduleFetchStrategy.DISABLED
    SubmoduleFetchStrategy.TOP_LEVEL_ONLY -> ApiSubmoduleFetchStrategy.TOP_LEVEL_ONLY
    SubmoduleFetchStrategy.FULLY_RECURSIVE -> ApiSubmoduleFetchStrategy.FULLY_RECURSIVE
}

fun ApiSubmoduleFetchStrategy.mapToModel() = when (this) {
    ApiSubmoduleFetchStrategy.DISABLED -> SubmoduleFetchStrategy.DISABLED
    ApiSubmoduleFetchStrategy.TOP_LEVEL_ONLY -> SubmoduleFetchStrategy.TOP_LEVEL_ONLY
    ApiSubmoduleFetchStrategy.FULLY_RECURSIVE -> SubmoduleFetchStrategy.FULLY_RECURSIVE
}

fun ShortestDependencyPath.mapToApi() = ApiShortestDependencyPath(
    scope = scope,
    projectIdentifier = projectIdentifier.mapToApi(),
    path = path.map { it.mapToApi() }
)

fun Package.mapToApi(
    shortestDependencyPaths: List<ShortestDependencyPath> = emptyList(),
    curations: List<PackageCuration> = emptyList()
) = ApiPackage(
    identifier = identifier.mapToApi(),
    purl = purl,
    cpe = cpe,
    authors = authors,
    declaredLicenses = declaredLicenses,
    processedDeclaredLicense = processedDeclaredLicense.mapToApi(),
    description = description,
    homepageUrl = homepageUrl,
    binaryArtifact = binaryArtifact.mapToApi(),
    sourceArtifact = sourceArtifact.mapToApi(),
    vcs = vcs.mapToApi(),
    vcsProcessed = vcsProcessed.mapToApi(),
    isMetadataOnly = isMetadataOnly,
    isModified = isModified,
    shortestDependencyPaths = shortestDependencyPaths.map { it.mapToApi() },
    curations = curations,
    sourceCodeOrigins = sourceCodeOrigins?.map { it.mapToApi() },
    labels = labels
)

fun PackageCurationData.mapToApi() = ApiPackageCurationData(
    comment = comment,
    purl = purl,
    cpe = cpe,
    authors = authors,
    concludedLicense = concludedLicense,
    description = description,
    homepageUrl = homepageUrl,
    binaryArtifact = binaryArtifact?.mapToApi(),
    sourceArtifact = sourceArtifact?.mapToApi(),
    vcs = vcs?.mapToApi(),
    isMetadataOnly = isMetadataOnly,
    isModified = isModified,
    declaredLicenseMapping = declaredLicenseMapping,
    sourceCodeOrigins = sourceCodeOrigins?.map { it.mapToApi() },
    labels = labels
)

fun RuleViolationResolution.mapToApi() = ApiRuleViolationResolution(
    message = message,
    reason = reason,
    comment = comment
)

fun VulnerabilityResolution.mapToApi() = ApiVulnerabilityResolution(
    externalId = externalId,
    reason = reason,
    comment = comment
)

fun VcsInfoCurationData.mapToApi() = ApiVcsInfoCurationData(
    type = type?.mapToApi(),
    url = url,
    revision = revision,
    path = path
)

fun ApiPackageFilters.mapToModel(): PackageFilters =
    PackageFilters(
        identifier = identifier?.mapToModel { it },
        purl = purl?.mapToModel { it },
        processedDeclaredLicense = processedDeclaredLicense?.mapToModel { it }
    )

fun ApiRuleViolationFilters.mapToModel(): RuleViolationFilters = RuleViolationFilters(resolved = resolved)

fun ApiVulnerabilityFilters.mapToModel(): VulnerabilityFilters = VulnerabilityFilters(resolved = resolved)

fun Project.mapToApi() = ApiProject(
    identifier = identifier.mapToApi(),
    cpe = cpe,
    definitionFilePath = definitionFilePath,
    authors = authors,
    declaredLicenses = declaredLicenses,
    processedDeclaredLicense = processedDeclaredLicense.mapToApi(),
    vcs = vcs.mapToApi(),
    vcsProcessed = vcsProcessed.mapToApi(),
    description = description,
    homepageUrl = homepageUrl,
    scopeNames = scopeNames
)

fun ContentManagementSection.mapToApi() = ApiContentManagementSection(
    id = id,
    isEnabled = isEnabled,
    markdown = markdown,
    updatedAt = updatedAt
)

fun OidcConfig.mapToApi() = ApiOidcConfig(accessTokenUrl = accessTokenUrl, clientId = clientId)

fun ApiVulnerabilityRating.mapToModel() = VulnerabilityRating.valueOf(name)

fun ApiVulnerabilityForRunsFilters.mapToModel() = VulnerabilityForRunsFilters(
    rating = rating?.mapToModel { ratingSet ->
        ratingSet.map { it.mapToModel() }.toSet()
    },
    identifier = identifier?.mapToModel { it },
    purl = purl?.mapToModel { it },
    externalId = externalId?.mapToModel { it }
)
