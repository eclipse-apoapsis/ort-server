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

package org.eclipse.apoapsis.ortserver.core.apiDocs

import io.github.smiley4.ktoropenapi.config.RouteConfig

import io.ktor.http.HttpStatusCode

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJob
import org.eclipse.apoapsis.ortserver.api.v1.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJob
import org.eclipse.apoapsis.ortserver.api.v1.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.EnvironmentConfig
import org.eclipse.apoapsis.ortserver.api.v1.model.EvaluatorJob
import org.eclipse.apoapsis.ortserver.api.v1.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.JobStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummaries
import org.eclipse.apoapsis.ortserver.api.v1.model.JobSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.NotifierJob
import org.eclipse.apoapsis.ortserver.api.v1.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunSummary
import org.eclipse.apoapsis.ortserver.api.v1.model.PackageManagerConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterJob
import org.eclipse.apoapsis.ortserver.api.v1.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.Repository
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType
import org.eclipse.apoapsis.ortserver.api.v1.model.ScannerJob
import org.eclipse.apoapsis.ortserver.api.v1.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.api.v1.model.SubmoduleFetchStrategy.FULLY_RECURSIVE
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.User
import org.eclipse.apoapsis.ortserver.api.v1.model.UserDisplayName
import org.eclipse.apoapsis.ortserver.api.v1.model.UserGroup
import org.eclipse.apoapsis.ortserver.api.v1.model.UserWithGroups
import org.eclipse.apoapsis.ortserver.api.v1.model.Username
import org.eclipse.apoapsis.ortserver.components.authorization.api.RepositoryRole
import org.eclipse.apoapsis.ortserver.shared.apimodel.InfrastructureService
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingData
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty
import org.eclipse.apoapsis.ortserver.shared.apimodel.asPresent
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.standardListQueryParameters

internal val fullJobConfigurations = JobConfigurations(
    analyzer = AnalyzerJobConfiguration(
        allowDynamicVersions = true,
        disabledPackageManagers = listOf("NPM", "SBT"),
        enabledPackageManagers = listOf("Gradle", "Maven"),
        environmentConfig = EnvironmentConfig(
            infrastructureServices = listOf(
                InfrastructureService(
                    name = "Artifactory",
                    url = "https://artifactory.example.org/repo",
                    description = "Our Artifactory server",
                    usernameSecretRef = "artifactoryUsername",
                    passwordSecretRef = "artifactoryPassword"
                )
            ),
            environmentDefinitions = mapOf(
                "maven" to listOf(
                    mapOf(
                        "service" to "Artifactory",
                        "id" to "repo"
                    )
                )
            )
        ),
        packageCurationProviders = listOf(
            ProviderPluginConfiguration(
                type = "ClearlyDefined",
                id = "ClearlyDefined",
                enabled = true,
                options = mapOf(
                    "serverUrl" to "https://api.clearlydefined.io",
                    "minTotalLicenseScore" to "0"
                )
            )
        ),
        packageManagerOptions = mapOf(
            "Gradle" to PackageManagerConfiguration(
                mustRunAfter = listOf("Maven"),
                options = mapOf("gradleVersion" to "8.1.1")
            )
        ),
        submoduleFetchStrategy = FULLY_RECURSIVE,
        skipExcluded = true
    ),
    advisor = AdvisorJobConfiguration(
        advisors = listOf("VulnerableCode"),
        skipExcluded = true
    ),
    scanner = ScannerJobConfiguration(
        projectScanners = listOf("SCANOSS"),
        scanners = listOf("ScanCode"),
        skipConcluded = true,
        skipExcluded = true
    ),
    evaluator = EvaluatorJobConfiguration(
        packageConfigurationProviders = listOf(ProviderPluginConfiguration(type = "OrtConfig")),
    ),
    reporter = ReporterJobConfiguration(formats = listOf("WebApp")),
    notifier = NotifierJobConfiguration(
        recipientAddresses = listOf("mail@example.com", "info@example.com"),
    ),
    ruleSet = "default"
)

private val minimalJobConfigurations = JobConfigurations(
    analyzer = AnalyzerJobConfiguration(
        skipExcluded = true
    ),
    advisor = AdvisorJobConfiguration(
        skipExcluded = true
    )
)

val jobs = Jobs(
    analyzer = AnalyzerJob(
        id = 1L,
        createdAt = CREATED_AT,
        configuration = fullJobConfigurations.analyzer,
        status = JobStatus.CREATED
    ),
    advisor = fullJobConfigurations.advisor?.let {
        AdvisorJob(
            id = 1L,
            createdAt = CREATED_AT,
            configuration = it,
            status = JobStatus.CREATED
        )
    },
    scanner = fullJobConfigurations.scanner?.let {
        ScannerJob(
            id = 1L,
            createdAt = CREATED_AT,
            configuration = it,
            status = JobStatus.CREATED
        )
    },
    evaluator = fullJobConfigurations.evaluator?.let {
        EvaluatorJob(
            id = 1L,
            createdAt = CREATED_AT,
            configuration = it,
            status = JobStatus.CREATED
        )
    },
    reporter = fullJobConfigurations.reporter?.let {
        ReporterJob(
            id = 1L,
            createdAt = CREATED_AT,
            configuration = it,
            status = JobStatus.CREATED,
            reportFilenames = listOf(
                "AsciiDoc_disclosure_document.pdf",
                "AsciiDoc_vulnerability_report.pdf",
                "scan-report-web-app.html"
            )
        )
    },
    notifier = fullJobConfigurations.notifier?.let {
        NotifierJob(
            id = 1L,
            createdAt = CREATED_AT,
            configuration = it,
            status = JobStatus.CREATED
        )
    }
)

/**
 * Create a [JobSummary] for a job that was created the provided [offset] duration ago.
 */
fun createJobSummary(offset: Duration, status: JobStatus = JobStatus.FINISHED): JobSummary {
    val createdAt = CREATED_AT - offset
    return JobSummary(
        id = 1L,
        createdAt = createdAt,
        startedAt = createdAt + 1.minutes,
        finishedAt = (createdAt + 2.minutes).takeIf { status == JobStatus.FINISHED },
        status = status
    )
}

val getRepositoryById: RouteConfig.() -> Unit = {
    operationId = "GetRepositoryById"
    summary = "Get details of a repository"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Repository> {
                example("Get Repository") {
                    value = Repository(
                        id = 1,
                        organizationId = 2,
                        productId = 3,
                        type = RepositoryType.GIT,
                        url = "https://example.com/org/repo.git"
                    )
                }
            }
        }
    }
}

val patchRepositoryById: RouteConfig.() -> Unit = {
    operationId = "PatchRepositoryById"
    summary = "Update a repository"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }
        jsonBody<UpdateRepository> {
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
            example("Update Repository") {
                value = UpdateRepository(
                    type = RepositoryType.GIT_REPO.asPresent(),
                    url = "https://example.com/org/updated-repo.git".asPresent(),
                    description = "Updated repository description.".asPresent()
                )
            }
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Repository> {
                example("Update Repository") {
                    value = Repository(
                        id = 1,
                        organizationId = 2,
                        productId = 3,
                        type = RepositoryType.GIT_REPO,
                        url = "https://example.com/org/updated-repo.git",
                        description = "Updated repository description."
                    )
                }
            }
        }
    }
}

val deleteRepositoryById: RouteConfig.() -> Unit = {
    operationId = "DeleteRepositoryById"
    summary = "Delete a repository"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Success"
        }
    }
}

val getOrtRunsByRepositoryId: RouteConfig.() -> Unit = {
    operationId = "getOrtRunsByRepositoryId"
    summary = "Get all ORT runs of a repository"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<OrtRunSummary>> {
                example("Get ORT runs") {
                    value = PagedResponse(
                        listOf(
                            OrtRunSummary(
                                id = 2,
                                index = 1,
                                organizationId = 1,
                                productId = 1,
                                repositoryId = 1,
                                revision = "main",
                                createdAt = CREATED_AT,
                                finishedAt = FINISHED_AT,
                                jobs = JobSummaries(
                                    analyzer = createJobSummary(10.minutes),
                                    advisor = createJobSummary(8.minutes),
                                    scanner = createJobSummary(8.minutes),
                                    evaluator = createJobSummary(6.minutes),
                                    reporter = createJobSummary(4.minutes)
                                ),
                                status = OrtRunStatus.FINISHED,
                                labels = mapOf("label key" to "label value"),
                                jobConfigContext = null,
                                resolvedJobConfigContext = "c80ef3bcd2bec428da923a188dd0870b1153995c",
                                userDisplayName = UserDisplayName("john.doe", "John Doe")
                            ),
                            OrtRunSummary(
                                id = 3,
                                index = 2,
                                organizationId = 1,
                                productId = 1,
                                repositoryId = 1,
                                revision = "main",
                                createdAt = CREATED_AT,
                                finishedAt = FINISHED_AT,
                                jobs = JobSummaries(
                                    analyzer = createJobSummary(5.minutes),
                                    advisor = createJobSummary(3.minutes),
                                    scanner = createJobSummary(3.minutes),
                                    evaluator = createJobSummary(1.minutes, JobStatus.RUNNING)
                                ),
                                status = OrtRunStatus.ACTIVE,
                                labels = mapOf("label key" to "label value"),
                                jobConfigContext = null,
                                resolvedJobConfigContext = "32f955941e94d0a318e1c985903f42af924e9050",
                                environmentConfigPath = null,
                                userDisplayName = UserDisplayName("john.doe", "John Doe")
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 2,
                            sortProperties = listOf(SortProperty("createdAt", SortDirection.DESCENDING)),
                        )
                    )
                }
            }
        }
    }
}

val postOrtRun: RouteConfig.() -> Unit = {
    operationId = "postOrtRun"
    summary = "Create an ORT run for a repository"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }

        jsonBody<CreateOrtRun> {
            example("Create ORT run using minimal job configurations (defaults)") {
                value = CreateOrtRun(
                    revision = "main",
                    jobConfigs = minimalJobConfigurations
                )
            }

            example("Create ORT run using full job configurations") {
                value = CreateOrtRun(
                    revision = "main",
                    jobConfigs = fullJobConfigurations,
                    labels = mapOf("label key" to "label value"),
                    path = "optional VCS sub-path",
                    jobConfigContext = "optional context",
                )
            }
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<OrtRun> {
                example("Create ORT run") {
                    value = OrtRun(
                        id = 1,
                        index = 2,
                        organizationId = 1,
                        productId = 1,
                        repositoryId = 1,
                        revision = "main",
                        createdAt = CREATED_AT,
                        jobConfigs = fullJobConfigurations,
                        resolvedJobConfigs = fullJobConfigurations,
                        jobs = jobs,
                        status = OrtRunStatus.CREATED,
                        finishedAt = null,
                        labels = mapOf("label key" to "label value"),
                        issues = emptyList(),
                        jobConfigContext = null,
                        resolvedJobConfigContext = null,
                        traceId = "35b67724-a85b-4cc3-b2a4-60fd914634e7"
                    )
                }
            }
        }
    }
}

val getOrtRunByIndex: RouteConfig.() -> Unit = {
    operationId = "getOrtRunByIndex"
    summary = "Get details of an ORT run of a repository"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }

        pathParameter<Long>("ortRunIndex") {
            description = "The index of an ORT run."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<OrtRun> {
                example("Get ORT run") {
                    value = OrtRun(
                        id = 1,
                        index = 2,
                        organizationId = 1,
                        productId = 1,
                        repositoryId = 1,
                        revision = "main",
                        createdAt = CREATED_AT,
                        jobConfigs = fullJobConfigurations,
                        resolvedJobConfigs = fullJobConfigurations,
                        jobs = jobs,
                        status = OrtRunStatus.ACTIVE,
                        finishedAt = null,
                        labels = mapOf("label key" to "label value"),
                        issues = emptyList(),
                        jobConfigContext = null,
                        resolvedJobConfigContext = "32f955941e94d0a318e1c985903f42af924e9050",
                        traceId = "35b67725-a85b-4cc3-b2a4-60fd914634e7",
                        environmentConfigPath = null
                    )
                }
            }
        }
    }
}

val deleteOrtRunByIndex: RouteConfig.() -> Unit = {
    operationId = "deleteOrtRunByIndex"
    summary = "Delete an ORT run of a repository"
    description = "This operation deletes an ORT run and all generated data, including the generated reports."
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }

        pathParameter<Long>("ortRunIndex") {
            description = "The index of an ORT run."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Successfully deleted the ORT run."
        }

        HttpStatusCode.NotFound to {
            description = "The ORT run does not exist."
        }
    }
}

val putRepositoryRoleToUser: RouteConfig.() -> Unit = {
    operationId = "PutRepositoryRoleToUser"
    summary = "Assign a repository role to a user"
    description = "Assign a repository role to a user. If the user already has another role for the same repository, " +
            "it will be replaced with the new one."
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }
        pathParameter<RepositoryRole>("role") {
            description = "The role to assign to the user."
        }

        jsonBody<Username> {
            example("Add user identified by username 'abc123'.") {
                value = Username(username = "abc123")
            }
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Successfully added the role to the user."
        }

        HttpStatusCode.NotFound to {
            description = "Repository or role not found."
        }
    }
}

val deleteRepositoryRoleFromUser: RouteConfig.() -> Unit = {
    operationId = "DeleteRepositoryRoleFromUser"
    summary = "Remove a repository role from a user"
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }
        pathParameter<RepositoryRole>("role") {
            description = "The role to remove from the user."
        }

        queryParameter<String>("username") {
            description = "The username of the user."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Successfully removed the role from the user."
        }

        HttpStatusCode.NotFound to {
            description = "Repository or role not found."
        }
    }
}

val getUsersForRepository: RouteConfig.() -> Unit = {
    operationId = "GetUsersForRepository"
    summary = "Get all users for a repository"
    description = "Get all users that have access rights for a repository, including the user privileges (groups) " +
            "the user has within the repository. Fields available for sorting: 'username', 'firstName', " +
            "'lastName', 'email', 'group'. NOTE: This endpoint supports only one sort field. All fields other than " +
            " the first one are ignored."
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }

        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<PagedResponse<UserWithGroups>> {
                example("Get users for repository") {
                    value = PagedResponse(
                        listOf(
                            UserWithGroups(
                                User(
                                    username = "jdoe",
                                    firstName = "John",
                                    lastName = "Doe",
                                    email = "johndoe@example.com"
                                ),
                                listOf(
                                    UserGroup.READERS,
                                    UserGroup.WRITERS
                                )
                            )
                        ),
                        PagingData(
                            limit = 20,
                            offset = 0,
                            totalCount = 1,
                            sortProperties = listOf(SortProperty("username", SortDirection.ASCENDING))
                        )
                    )
                }
            }
        }
    }
}
