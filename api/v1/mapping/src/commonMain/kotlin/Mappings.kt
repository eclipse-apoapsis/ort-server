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

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJob as ApiAdvisorJob
import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJobConfiguration as ApiAdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJob as ApiAnalyzerJob
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJobConfiguration as ApiAnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.CredentialsType as ApiCredentialsType
import org.eclipse.apoapsis.ortserver.api.v1.model.EnvironmentConfig as ApiEnvironmentConfig
import org.eclipse.apoapsis.ortserver.api.v1.model.EnvironmentVariableDeclaration as ApiEnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.api.v1.model.EvaluatorJob as ApiEvaluatorJob
import org.eclipse.apoapsis.ortserver.api.v1.model.EvaluatorJobConfiguration as ApiEvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.InfrastructureService as ApiInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.Issue as ApiIssue
import org.eclipse.apoapsis.ortserver.api.v1.model.JiraNotificationConfiguration as ApiJiraNotificationConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.JiraRestClientConfiguration as ApiJiraRestClientConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations as ApiJobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.JobStatus as ApiJobStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummaries as ApiJobSummaries
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummary as ApiJobSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs as ApiJobs
import org.eclipse.apoapsis.ortserver.api.v1.model.MailNotificationConfiguration as ApiMailNotificationConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.MailServerConfiguration as ApiMailServerConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.NotifierJob as ApiNotifierJob
import org.eclipse.apoapsis.ortserver.api.v1.model.NotifierJobConfiguration as ApiNotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.OptionalValue as ApiOptionalValue
import org.eclipse.apoapsis.ortserver.api.v1.model.Organization as ApiOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun as ApiOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus as ApiOrtRunStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunSummary as ApiOrtRunSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageManagerConfiguration as ApiPackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.PagedResponse2 as ApiPagedResponse2
import org.eclipse.apoapsis.ortserver.api.v1.model.PagingOptions as ApiPagingOptions
import org.eclipse.apoapsis.ortserver.api.v1.model.PluginConfiguration as ApiPluginConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.Product as ApiProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.ProviderPluginConfiguration as ApiProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterAsset as ApiReporterAsset
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterJob as ApiReporterJob
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterJobConfiguration as ApiReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.Repository as ApiRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType as ApiRepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.ScannerJob as ApiScannerJob
import org.eclipse.apoapsis.ortserver.api.v1.model.ScannerJobConfiguration as ApiScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.Secret as ApiSecret
import org.eclipse.apoapsis.ortserver.api.v1.model.SortDirection as ApiSortDirection
import org.eclipse.apoapsis.ortserver.api.v1.model.SortProperty as ApiSortProperty
import org.eclipse.apoapsis.ortserver.api.v1.model.SourceCodeOrigin as ApiSourceCodeOrigin
import org.eclipse.apoapsis.ortserver.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.CredentialsType
import org.eclipse.apoapsis.ortserver.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.model.EnvironmentVariableDeclaration
import org.eclipse.apoapsis.ortserver.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.InfrastructureServiceDeclaration
import org.eclipse.apoapsis.ortserver.model.JiraNotificationConfiguration
import org.eclipse.apoapsis.ortserver.model.JiraRestClientConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.JobStatus
import org.eclipse.apoapsis.ortserver.model.Jobs
import org.eclipse.apoapsis.ortserver.model.MailNotificationConfiguration
import org.eclipse.apoapsis.ortserver.model.MailServerConfiguration
import org.eclipse.apoapsis.ortserver.model.NotifierJob
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.PluginConfiguration
import org.eclipse.apoapsis.ortserver.model.Product
import org.eclipse.apoapsis.ortserver.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.model.ReporterAsset
import org.eclipse.apoapsis.ortserver.model.ReporterJob
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.ScannerJob
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.Secret
import org.eclipse.apoapsis.ortserver.model.SourceCodeOrigin
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField

fun AdvisorJob.mapToApi() =
    ApiAdvisorJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
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
    ApiAdvisorJobConfiguration(advisors, skipExcluded, config?.mapValues { it.value.mapToApi() })

fun ApiAdvisorJobConfiguration.mapToModel() =
    AdvisorJobConfiguration(advisors, skipExcluded, config?.mapValues { it.value.mapToModel() })

fun AnalyzerJob.mapToApi() =
    ApiAnalyzerJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
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
        allowDynamicVersions,
        disabledPackageManagers,
        enabledPackageManagers,
        environmentConfig?.mapToApi(),
        packageCurationProviders.map { it.mapToApi() },
        packageManagerOptions?.mapValues { it.value.mapToApi() },
        repositoryConfigPath,
        skipExcluded
    )

fun ApiAnalyzerJobConfiguration.mapToModel() =
    AnalyzerJobConfiguration(
        allowDynamicVersions,
        disabledPackageManagers,
        enabledPackageManagers,
        environmentConfig?.mapToModel(),
        packageCurationProviders.map { it.mapToModel() },
        packageManagerOptions?.mapValues { it.value.mapToModel() },
        repositoryConfigPath,
        skipExcluded
    )

fun EvaluatorJob.mapToApi() =
    ApiEvaluatorJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
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
        copyrightGarbageFile,
        licenseClassificationsFile,
        packageConfigurationProviders.map { it.mapToApi() },
        resolutionsFile,
        ruleSet
    )

fun ApiEvaluatorJobConfiguration.mapToModel() =
    EvaluatorJobConfiguration(
        copyrightGarbageFile,
        licenseClassificationsFile,
        packageConfigurationProviders.map { it.mapToModel() },
        resolutionsFile,
        ruleSet
    )

fun Issue.mapToApi() = ApiIssue(timestamp, source, message, severity)

fun JobStatus.mapToApi() = ApiJobStatus.valueOf(name)

fun ApiJobStatus.mapToModel() = JobStatus.valueOf(name)

fun JobConfigurations.mapToApi() =
    ApiJobConfigurations(
        analyzer.mapToApi(),
        advisor?.mapToApi(),
        scanner?.mapToApi(),
        evaluator?.mapToApi(),
        reporter?.mapToApi(),
        notifier?.mapToApi(),
        parameters
    )

fun ApiJobConfigurations.mapToModel() =
    JobConfigurations(
        analyzer.mapToModel(),
        advisor?.mapToModel(),
        scanner?.mapToModel(),
        evaluator?.mapToModel(),
        reporter?.mapToModel(),
        notifier?.mapToModel(),
        parameters
    )

fun Jobs.mapToApi() =
    ApiJobs(
        analyzer?.mapToApi(),
        advisor?.mapToApi(),
        scanner?.mapToApi(),
        evaluator?.mapToApi(),
        reporter?.mapToApi(),
        notifier?.mapToApi()
    )

fun Jobs.mapToApiSummary() =
    ApiJobSummaries(
        analyzer?.mapToApiSummary(),
        advisor?.mapToApiSummary(),
        scanner?.mapToApiSummary(),
        evaluator?.mapToApiSummary(),
        reporter?.mapToApiSummary()
    )

fun Organization.mapToApi() = ApiOrganization(id, name, description)

fun OrtRun.mapToApi(jobs: ApiJobs) =
    ApiOrtRun(
        id = id,
        index = index,
        organizationId,
        productId,
        repositoryId,
        revision,
        path,
        createdAt,
        finishedAt,
        jobConfigs.mapToApi(),
        resolvedJobConfigs?.mapToApi(),
        jobs,
        status.mapToApi(),
        labels,
        issues = issues.map { it.mapToApi() },
        jobConfigContext,
        resolvedJobConfigContext
    )

fun OrtRun.mapToApiSummary(jobs: ApiJobSummaries) =
    ApiOrtRunSummary(
        id = id,
        index = index,
        organizationId = organizationId,
        productId = productId,
        repositoryId = repositoryId,
        revision = revision,
        path = path,
        createdAt = createdAt,
        finishedAt = finishedAt,
        jobs = jobs,
        status = status.mapToApi(),
        labels = labels,
        jobConfigContext = jobConfigContext,
        resolvedJobConfigContext = resolvedJobConfigContext
    )

fun OrtRunStatus.mapToApi() = ApiOrtRunStatus.valueOf(name)

fun Product.mapToApi() = ApiProduct(id, organizationId, name, description)

fun Repository.mapToApi() = ApiRepository(id, organizationId, productId, type.mapToApi(), url)

fun RepositoryType.mapToApi() = ApiRepositoryType.valueOf(name)

fun ApiRepositoryType.mapToModel() = RepositoryType.forName(name)

fun <T> ApiOptionalValue<T>.mapToModel() = mapToModel { it }

fun <IN, OUT> ApiOptionalValue<IN>.mapToModel(valueMapping: (IN) -> OUT): OptionalValue<OUT> =
    when (this) {
        is ApiOptionalValue.Present -> OptionalValue.Present(valueMapping(value))
        is ApiOptionalValue.Absent -> OptionalValue.Absent
    }

fun ReporterJob.mapToApi() =
    ApiReporterJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi(),
        filenames
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
        copyrightGarbageFile,
        formats,
        howToFixTextProviderFile,
        licenseClassificationsFile,
        packageConfigurationProviders.map { it.mapToApi() },
        resolutionsFile,
        customLicenseTextDir,
        assetFiles.map { it.mapToApi() },
        assetDirectories.map { it.mapToApi() },
        config?.mapValues { it.value.mapToApi() }
    )

fun NotifierJob.mapToApi() =
    ApiNotifierJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
    )

fun NotifierJobConfiguration.mapToApi() =
    ApiNotifierJobConfiguration(
        notifierRules = notifierRules,
        resolutionsFile = resolutionsFile,
        mail = mail?.mapToApi(),
        jira = jira?.mapToApi()
    )

fun ApiNotifierJobConfiguration.mapToModel() =
    NotifierJobConfiguration(
        notifierRules = notifierRules,
        resolutionsFile = resolutionsFile,
        mail = mail?.mapToModel(),
        jira = jira?.mapToModel()
    )

fun ApiReporterJobConfiguration.mapToModel() =
    ReporterJobConfiguration(
        copyrightGarbageFile,
        formats,
        howToFixTextProviderFile,
        licenseClassificationsFile,
        packageConfigurationProviders.map { it.mapToModel() },
        resolutionsFile,
        customLicenseTextDir,
        assetFiles.map { it.mapToModel() },
        assetDirectories.map { it.mapToModel() },
        config?.mapValues { it.value.mapToModel() }
    )

fun ScannerJob.mapToApi() =
    ApiScannerJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
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
    detectedLicenseMappings,
    ignorePatterns,
    projectScanners,
    scanners,
    skipConcluded,
    skipExcluded,
    sourceCodeOrigins?.map { it.mapToApi() },
    config?.mapValues { it.value.mapToApi() }
)

fun ApiScannerJobConfiguration.mapToModel() = ScannerJobConfiguration(
    detectedLicenseMappings,
    ignorePatterns,
    projectScanners,
    scanners,
    skipConcluded,
    skipExcluded,
    sourceCodeOrigins?.map { it.mapToModel() },
    config?.mapValues { it.value.mapToModel() }
)

fun Secret.mapToApi() = ApiSecret(name, description)

fun InfrastructureService.mapToApi() =
    ApiInfrastructureService(
        name,
        url,
        description,
        usernameSecret.name,
        passwordSecret.name,
        credentialsTypes.mapToApi()
    )

fun ApiInfrastructureService.mapToModel() =
    InfrastructureServiceDeclaration(
        name,
        url,
        description,
        usernameSecretRef,
        passwordSecretRef,
        credentialsTypes.mapToModel()
    )

fun InfrastructureServiceDeclaration.mapToApi() =
    ApiInfrastructureService(name, url, description, usernameSecret, passwordSecret, credentialsTypes.mapToApi())

fun ApiEnvironmentVariableDeclaration.mapToModel() = EnvironmentVariableDeclaration(name, secretName)

fun EnvironmentVariableDeclaration.mapToApi() = ApiEnvironmentVariableDeclaration(name, secretName)

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

fun CredentialsType.mapToApi() = ApiCredentialsType.valueOf(name)

fun Set<CredentialsType>.mapToApi(): Set<ApiCredentialsType> =
    mapTo(EnumSet.noneOf(ApiCredentialsType::class.java)) { it.mapToApi() }

fun ApiCredentialsType.mapToModel() = CredentialsType.valueOf(name)

fun Set<ApiCredentialsType>.mapToModel(): Set<CredentialsType> =
    mapTo(EnumSet.noneOf(CredentialsType::class.java)) { it.mapToModel() }

fun PackageManagerConfiguration.mapToApi() =
    ApiPackageManagerConfiguration(mustRunAfter = mustRunAfter, options = options)

fun ApiPackageManagerConfiguration.mapToModel() =
    PackageManagerConfiguration(mustRunAfter = mustRunAfter, options = options)

fun PluginConfiguration.mapToApi() = ApiPluginConfiguration(options = options, secrets = secrets)

fun ApiPluginConfiguration.mapToModel() = PluginConfiguration(options = options, secrets = secrets)

fun ProviderPluginConfiguration.mapToApi() =
    ApiProviderPluginConfiguration(
        type = type,
        id = id,
        enabled = enabled,
        options = options,
        secrets = secrets
    )

fun ApiProviderPluginConfiguration.mapToModel() =
    ProviderPluginConfiguration(
        type = type,
        id = id,
        enabled = enabled,
        options = options,
        secrets = secrets
    )

fun ReporterAsset.mapToApi() =
    ApiReporterAsset(sourcePath, targetFolder, targetName)

fun ApiReporterAsset.mapToModel() =
    ReporterAsset(sourcePath, targetFolder, targetName)

fun MailNotificationConfiguration.mapToApi() =
    ApiMailNotificationConfiguration(
        recipientAddresses = recipientAddresses,
        mailServerConfiguration = mailServerConfiguration?.mapToApi()
    )

fun ApiMailNotificationConfiguration.mapToModel() =
    MailNotificationConfiguration(
        recipientAddresses = recipientAddresses,
        mailServerConfiguration = mailServerConfiguration?.mapToModel()
    )

fun JiraNotificationConfiguration.mapToApi() =
    ApiJiraNotificationConfiguration(
        jiraRestClientConfiguration = jiraRestClientConfiguration?.mapToApi()
    )

fun ApiJiraNotificationConfiguration.mapToModel() =
    JiraNotificationConfiguration(
        jiraRestClientConfiguration = jiraRestClientConfiguration?.mapToModel()
    )

fun MailServerConfiguration.mapToApi() =
    ApiMailServerConfiguration(
        hostName = hostName,
        port = port,
        username = username,
        password = password,
        useSsl = useSsl,
        fromAddress = fromAddress
    )

fun ApiMailServerConfiguration.mapToModel() =
    MailServerConfiguration(
        hostName = hostName,
        port = port,
        username = username,
        password = password,
        useSsl = useSsl,
        fromAddress = fromAddress
    )

fun JiraRestClientConfiguration.mapToApi() =
    ApiJiraRestClientConfiguration(
        serverUrl = serverUrl,
        username = username,
        password = password
    )

fun ApiJiraRestClientConfiguration.mapToModel() =
    JiraRestClientConfiguration(
        serverUrl = serverUrl,
        username = username,
        password = password
    )

fun ApiPagingOptions.mapToModel() =
    ListQueryParameters(
        sortFields = sortProperties?.map { it.mapToModel() }.orEmpty(),
        limit = limit,
        offset = offset
    )

fun <T, E> ListQueryResult<T>.mapToApi(mapValues: (T) -> E) =
    ApiPagedResponse2(
        data = data.map(mapValues),
        pagination = params.mapToApi().toPagingData(totalCount)
    )

fun ListQueryParameters.mapToApi() =
    ApiPagingOptions(
        limit = limit,
        offset = offset,
        sortProperties = sortFields.map { it.mapToApi() }
    )

fun ApiSortProperty.mapToModel() = OrderField(name, direction.mapToModel())

fun OrderField.mapToApi() = ApiSortProperty(name, direction.mapToApi())

fun ApiSortDirection.mapToModel() =
    when (this) {
        ApiSortDirection.ASCENDING -> OrderDirection.ASCENDING
        ApiSortDirection.DESCENDING -> OrderDirection.DESCENDING
    }

fun OrderDirection.mapToApi() =
    when (this) {
        OrderDirection.ASCENDING -> ApiSortDirection.ASCENDING
        OrderDirection.DESCENDING -> ApiSortDirection.DESCENDING
    }

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
