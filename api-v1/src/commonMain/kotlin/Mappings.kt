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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.server.api.v1

import org.ossreviewtoolkit.server.api.v1.AdvisorJob as ApiAdvisorJob
import org.ossreviewtoolkit.server.api.v1.AdvisorJobConfiguration as ApiAdvisorJobConfiguration
import org.ossreviewtoolkit.server.api.v1.AnalyzerJob as ApiAnalyzerJob
import org.ossreviewtoolkit.server.api.v1.AnalyzerJobConfiguration as ApiAnalyzerJobConfiguration
import org.ossreviewtoolkit.server.api.v1.EvaluatorJob as ApiEvaluatorJob
import org.ossreviewtoolkit.server.api.v1.EvaluatorJobConfiguration as ApiEvaluatorJobConfiguration
import org.ossreviewtoolkit.server.api.v1.InfrastructureService as ApiInfrastructureService
import org.ossreviewtoolkit.server.api.v1.JobConfigurations as ApiJobConfigurations
import org.ossreviewtoolkit.server.api.v1.JobStatus as ApiJobStatus
import org.ossreviewtoolkit.server.api.v1.Organization as ApiOrganization
import org.ossreviewtoolkit.server.api.v1.OrtRun as ApiOrtRun
import org.ossreviewtoolkit.server.api.v1.OrtRunStatus as ApiOrtRunStatus
import org.ossreviewtoolkit.server.api.v1.Product as ApiProduct
import org.ossreviewtoolkit.server.api.v1.ReporterJob as ApiReporterJob
import org.ossreviewtoolkit.server.api.v1.ReporterJobConfiguration as ApiReporterJobConfiguration
import org.ossreviewtoolkit.server.api.v1.Repository as ApiRepository
import org.ossreviewtoolkit.server.api.v1.RepositoryType as ApiRepositoryType
import org.ossreviewtoolkit.server.api.v1.ScannerJob as ApiScannerJob
import org.ossreviewtoolkit.server.api.v1.ScannerJobConfiguration as ApiScannerJobConfiguration
import org.ossreviewtoolkit.server.api.v1.Secret as ApiSecret
import org.ossreviewtoolkit.server.model.AdvisorJob
import org.ossreviewtoolkit.server.model.AdvisorJobConfiguration
import org.ossreviewtoolkit.server.model.AnalyzerJob
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.EvaluatorJob
import org.ossreviewtoolkit.server.model.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.JobStatus
import org.ossreviewtoolkit.server.model.Organization
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.OrtRunStatus
import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.ReporterJob
import org.ossreviewtoolkit.server.model.ReporterJobConfiguration
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.ScannerJob
import org.ossreviewtoolkit.server.model.ScannerJobConfiguration
import org.ossreviewtoolkit.server.model.Secret
import org.ossreviewtoolkit.server.model.util.OptionalValue

fun AdvisorJob.mapToApi() =
    ApiAdvisorJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
    )

fun AdvisorJobConfiguration.mapToApi() = ApiAdvisorJobConfiguration(advisors)

fun ApiAdvisorJobConfiguration.mapToModel() = AdvisorJobConfiguration(advisors)

fun AnalyzerJob.mapToApi() =
    ApiAnalyzerJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi(),
        repositoryUrl,
        repositoryRevision
    )

fun AnalyzerJobConfiguration.mapToApi() = ApiAnalyzerJobConfiguration(allowDynamicVersions)

fun ApiAnalyzerJobConfiguration.mapToModel() = AnalyzerJobConfiguration(allowDynamicVersions)

fun EvaluatorJob.mapToApi() =
    ApiEvaluatorJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
    )

fun EvaluatorJobConfiguration.mapToApi() = ApiEvaluatorJobConfiguration(ruleSet)

fun ApiEvaluatorJobConfiguration.mapToModel() = EvaluatorJobConfiguration(ruleSet)

fun JobStatus.mapToApi() = ApiJobStatus.valueOf(name)

fun ApiJobStatus.mapToModel() = JobStatus.valueOf(name)

fun JobConfigurations.mapToApi() =
    ApiJobConfigurations(
        analyzer.mapToApi(),
        advisor?.mapToApi(),
        scanner?.mapToApi(),
        evaluator?.mapToApi(),
        reporter?.mapToApi()
    )

fun ApiJobConfigurations.mapToModel() =
    JobConfigurations(
        analyzer.mapToModel(),
        advisor?.mapToModel(),
        scanner?.mapToModel(),
        evaluator?.mapToModel(),
        reporter?.mapToModel()
    )

fun Organization.mapToApi() = ApiOrganization(id, name, description)

fun OrtRun.mapToApi() =
    ApiOrtRun(id = id, index = index, repositoryId, revision, createdAt, jobs.mapToApi(), status.mapToApi(), labels)

fun OrtRunStatus.mapToApi() = ApiOrtRunStatus.valueOf(name)

fun Product.mapToApi() = ApiProduct(id, name, description)

fun Repository.mapToApi() = ApiRepository(id, type.mapToApi(), url)

fun RepositoryType.mapToApi() = ApiRepositoryType.valueOf(name)

fun ApiRepositoryType.mapToModel() = RepositoryType.valueOf(name)

fun OptionalValue<ApiRepositoryType>.mapToModel() = map { it.mapToModel() }

fun ReporterJob.mapToApi() =
    ApiReporterJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
    )

fun ReporterJobConfiguration.mapToApi() = ApiReporterJobConfiguration(formats)

fun ApiReporterJobConfiguration.mapToModel() = ReporterJobConfiguration(formats)

fun ScannerJob.mapToApi() =
    ApiScannerJob(
        id,
        createdAt,
        startedAt,
        finishedAt,
        configuration.mapToApi(),
        status.mapToApi()
    )

fun ScannerJobConfiguration.mapToApi() = ApiScannerJobConfiguration(skipExcluded)

fun ApiScannerJobConfiguration.mapToModel() = ScannerJobConfiguration(skipExcluded)

fun Secret.mapToApi() = ApiSecret(name, description)

fun InfrastructureService.mapToApi() =
    ApiInfrastructureService(name, url, description, usernameSecret.name, passwordSecret.name)
