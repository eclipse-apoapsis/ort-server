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

import org.ossreviewtoolkit.server.api.v1.AdvisorJobConfiguration
import org.ossreviewtoolkit.server.api.v1.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.api.v1.CreateOrtRun
import org.ossreviewtoolkit.server.api.v1.EvaluatorJobConfiguration
import org.ossreviewtoolkit.server.api.v1.JobConfigurations
import org.ossreviewtoolkit.server.api.v1.OrtRun
import org.ossreviewtoolkit.server.api.v1.Repository
import org.ossreviewtoolkit.server.api.v1.RepositoryType
import org.ossreviewtoolkit.server.api.v1.ScannerJobConfiguration
import org.ossreviewtoolkit.server.api.v1.UpdateRepository

private val jobConfigurations = JobConfigurations(
    analyzer = AnalyzerJobConfiguration(),
    advisor = AdvisorJobConfiguration(advisors = listOf("VulnerableCode")),
    scanner = ScannerJobConfiguration(),
    evaluator = EvaluatorJobConfiguration()
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
                CreateOrtRun("main", jobConfigurations)
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
                            repositoryId = 1,
                            revision = "main",
                            createdAt = Clock.System.now(),
                            jobs = jobConfigurations
                        )
                    )
                )
            }
        }
    }
}

fun getOrtRunByIndex(json: Json): OpenApiRoute.() -> Unit = {
    operationId = "getOrtRunByIndex"
    summary = "Get details of an ORT run."
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
                            repositoryId = 1,
                            revision = "main",
                            createdAt = Clock.System.now(),
                            jobs = jobConfigurations
                        )
                    )
                )
            }
        }
    }
}
