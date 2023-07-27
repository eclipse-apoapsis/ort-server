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

package org.ossreviewtoolkit.server.core.apiDocs

import io.github.smiley4.ktorswaggerui.dsl.OpenApiRoute

import io.ktor.http.HttpStatusCode

import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.server.api.v1.AdvisorJob
import org.ossreviewtoolkit.server.api.v1.AdvisorJobConfiguration
import org.ossreviewtoolkit.server.api.v1.AnalyzerJob
import org.ossreviewtoolkit.server.api.v1.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.api.v1.CreateOrtRun
import org.ossreviewtoolkit.server.api.v1.CreateSecret
import org.ossreviewtoolkit.server.api.v1.EnvironmentConfig
import org.ossreviewtoolkit.server.api.v1.EvaluatorJob
import org.ossreviewtoolkit.server.api.v1.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.api.v1.InfrastructureService
import org.ossreviewtoolkit.server.api.v1.JobConfigurations
import org.ossreviewtoolkit.server.api.v1.JobStatus
import org.ossreviewtoolkit.server.api.v1.Jobs
import org.ossreviewtoolkit.server.api.v1.OrtRun
import org.ossreviewtoolkit.server.api.v1.OrtRunStatus
import org.ossreviewtoolkit.server.api.v1.ReporterJob
import org.ossreviewtoolkit.server.api.v1.ReporterJobConfiguration
import org.ossreviewtoolkit.server.api.v1.Repository
import org.ossreviewtoolkit.server.api.v1.RepositoryType
import org.ossreviewtoolkit.server.api.v1.ScannerJob
import org.ossreviewtoolkit.server.api.v1.ScannerJobConfiguration
import org.ossreviewtoolkit.server.api.v1.Secret
import org.ossreviewtoolkit.server.api.v1.UpdateRepository
import org.ossreviewtoolkit.server.api.v1.UpdateSecret
import org.ossreviewtoolkit.server.model.util.asPresent

private val jobConfigurations = JobConfigurations(
    analyzer = AnalyzerJobConfiguration(
        allowDynamicVersions = true,
        environmentConfig = EnvironmentConfig(
            infrastructureServices = listOf(
                InfrastructureService(
                    "Artifactory",
                    "https://artifactory.example.org/repo",
                    "Our Artifactory server",
                    "artifactoryUsername",
                    "artifactoryPassword"
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
        )
    ),
    advisor = AdvisorJobConfiguration(advisors = listOf("VulnerableCode")),
    scanner = ScannerJobConfiguration(),
    evaluator = EvaluatorJobConfiguration(),
    reporter = ReporterJobConfiguration(formats = listOf("WebApp"))
)

val jobs = Jobs(
    analyzer = AnalyzerJob(
        id = 1L,
        createdAt = Clock.System.now(),
        configuration = jobConfigurations.analyzer,
        status = JobStatus.CREATED,
        repositoryUrl = "",
        repositoryRevision = ""
    ),
    advisor = AdvisorJob(
        id = 1L,
        createdAt = Clock.System.now(),
        configuration = jobConfigurations.advisor!!,
        status = JobStatus.CREATED
    ),
    scanner = ScannerJob(
        id = 1L,
        createdAt = Clock.System.now(),
        configuration = jobConfigurations.scanner!!,
        status = JobStatus.CREATED
    ),
    evaluator = EvaluatorJob(
        id = 1L,
        createdAt = Clock.System.now(),
        configuration = jobConfigurations.evaluator!!,
        status = JobStatus.CREATED
    ),
    reporter = ReporterJob(
        id = 1L,
        createdAt = Clock.System.now(),
        configuration = jobConfigurations.reporter!!,
        status = JobStatus.CREATED
    )
)

val getRepositoryById: OpenApiRoute.() -> Unit = {
    operationId = "GetRepositoryById"
    summary = "Get details of a repository."
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
                example("Get Repository", Repository(1, RepositoryType.GIT, "https://example.com/org/repo.git"))
            }
        }
    }
}

val patchRepositoryById: OpenApiRoute.() -> Unit = {
    operationId = "PatchRepositoryById"
    summary = "Update a repository."
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }
        jsonBody<UpdateRepository> {
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Repository> {
                example("Update Repository", Repository(1, RepositoryType.GIT, "https://example.com/org/repo.git"))
            }
        }
    }
}

val deleteRepositoryById: OpenApiRoute.() -> Unit = {
    operationId = "DeleteRepositoryById"
    summary = "Delete a repository."
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

fun getOrtRuns(json: Json): OpenApiRoute.() -> Unit = {
    operationId = "getOrtRuns"
    summary = "Get all ORT runs of a repository."
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
            jsonBody<OrtRun> {
                example(
                    "Get ORT runs",
                    json.encodeToString(
                        listOf(
                            OrtRun(
                                id = 2,
                                index = 1,
                                repositoryId = 1,
                                revision = "main",
                                createdAt = Clock.System.now(),
                                config = jobConfigurations,
                                jobs = jobs,
                                status = OrtRunStatus.FINISHED,
                                labels = mapOf("label key" to "label value")
                            ),
                            OrtRun(
                                id = 3,
                                index = 2,
                                repositoryId = 1,
                                revision = "main",
                                createdAt = Clock.System.now(),
                                config = jobConfigurations,
                                jobs = jobs,
                                status = OrtRunStatus.ACTIVE,
                                labels = mapOf("label key" to "label value")
                            )
                        )
                    )
                )
            }
        }
    }
}

fun postOrtRun(json: Json): OpenApiRoute.() -> Unit = {
    operationId = "postOrtRun"
    summary = "Create an ORT run for a repository."
    tags = listOf("Repositories")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }

        jsonBody<CreateOrtRun> {
            example(
                "Create ORT run",
                CreateOrtRun("main", jobConfigurations, mapOf("label key" to "label value"))
            )
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<OrtRun> {
                example(
                    "Create ORT run",
                    json.encodeToString(
                        OrtRun(
                            id = 1,
                            index = 2,
                            repositoryId = 1,
                            revision = "main",
                            createdAt = Clock.System.now(),
                            config = jobConfigurations,
                            jobs = jobs,
                            status = OrtRunStatus.CREATED,
                            labels = mapOf("label key" to "label value")
                        )
                    )
                )
            }
        }
    }
}

fun getOrtRunByIndex(json: Json): OpenApiRoute.() -> Unit = {
    operationId = "getOrtRunByIndex"
    summary = "Get details of an ORT run of a repository."
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
                example(
                    "Get ORT run",
                    json.encodeToString(
                        OrtRun(
                            id = 1,
                            index = 2,
                            repositoryId = 1,
                            revision = "main",
                            createdAt = Clock.System.now(),
                            config = jobConfigurations,
                            jobs = jobs,
                            status = OrtRunStatus.ACTIVE,
                            labels = mapOf("label key" to "label value")
                        )
                    )
                )
            }
        }
    }
}

val getSecretsByRepositoryId: OpenApiRoute.() -> Unit = {
    operationId = "GetSecretsByRepositoryId"
    summary = "Get all secrets of a repository."
    tags = listOf("Secrets")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The ID of a repository."
        }
        standardListQueryParameters()
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<List<Secret>> {
                example(
                    "Get all secrets of a repository",
                    listOf(
                        Secret(
                            "rsa",
                            "ssh rsa certificate"
                        ),
                        Secret(
                            "secret",
                            "another secret"
                        )
                    )
                )
            }
        }
    }
}

val getSecretByRepositoryIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "GetSecretByRepositoryIdAndName"
    summary = "Get details of a secret of a repository."
    tags = listOf("Secrets")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }
        pathParameter<String>("secretName") {
            description = "The secret's name."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Secret> {
                example(
                    "Get Secret",
                    Secret(
                        "rsa",
                        "rsa certificate"
                    )
                )
            }
        }
    }
}

val postSecretForRepository: OpenApiRoute.() -> Unit = {
    operationId = "PostSecretForRepository"
    summary = "Create a secret for a repository."
    tags = listOf("Secrets")

    request {
        jsonBody<CreateSecret> {
            example(
                "Create Secret",
                CreateSecret(
                    "New secret",
                    "r3p0-s3cr3t-08_15",
                    "The new repo secret"
                )
            )
        }
    }

    response {
        HttpStatusCode.Created to {
            description = "Success"
            jsonBody<Secret> {
                example(
                    "Create Secret",
                    Secret(
                        "rsa",
                        "New secret"
                    )
                )
            }
        }
    }
}

val patchSecretByRepositoryIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "PatchSecretByRepositoryIdIdAndName"
    summary = "Update a secret of a repository."
    tags = listOf("Secrets")

    request {
        pathParameter<Long>("repositoryIdId") {
            description = "The repository's ID."
        }
        pathParameter<String>("secretName") {
            description = "The secret's name."
        }
        jsonBody<UpdateSecret> {
            example(
                "Update Secret",
                UpdateSecret(
                    "My updated Secret".asPresent(),
                    "Updated description".asPresent()
                )
            )
            description = "Set the values that should be updated. To delete a value, set it explicitly to null."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<Secret> {
                example(
                    "Update Secret",
                    Secret(
                        "My updated Secret",
                        "Updated description."
                    )
                )
            }
        }
    }
}

val deleteSecretByRepositoryIdAndName: OpenApiRoute.() -> Unit = {
    operationId = "DeleteSecretByRepositoryIdAndName"
    summary = "Delete a secret from a repository."
    tags = listOf("Secrets")

    request {
        pathParameter<Long>("repositoryId") {
            description = "The repository's ID."
        }
        pathParameter<String>("secretName") {
            description = "The secret's name."
        }
    }

    response {
        HttpStatusCode.NoContent to {
            description = "Success"
        }
    }
}
