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

import org.ossreviewtoolkit.server.api.v1.AnalyzerJob
import org.ossreviewtoolkit.server.api.v1.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.api.v1.AnalyzerJobStatus

fun startAnalyzerJob(json: Json): OpenApiRoute.() -> Unit = {
    operationId = "startAnalyzerJob"
    summary = "Start a scheduled analyzer job."
    tags = listOf("Jobs")

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<AnalyzerJob> {
                example(
                    "Start analyzer job",
                    // TODO: With ktor-swagger-ui kotlinx.datetime.Instant is wrongly serialized. Manually serialize
                    //       this example as a workaround.
                    json.encodeToString(
                        AnalyzerJob(
                            id = 1,
                            createdAt = Clock.System.now(),
                            startedAt = Clock.System.now(),
                            finishedAt = null,
                            configuration = AnalyzerJobConfiguration(),
                            status = AnalyzerJobStatus.RUNNING,
                            repositoryUrl = "https://example.com/git/repository.git",
                            repositoryRevision = "main"
                        )
                    )
                )
            }
        }
    }
}

fun getAnalyzerJobById(json: Json): OpenApiRoute.() -> Unit = {
    operationId = "getAnalyzerJobById"
    summary = "Get details of an analyzer job."
    tags = listOf("Jobs")

    request {
        pathParameter<Long>("id") {
            description = "The analyzer job's ID."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<AnalyzerJob> {
                example(
                    "Get analyzer job",
                    json.encodeToString(
                        AnalyzerJob(
                            id = 1,
                            createdAt = Clock.System.now(),
                            startedAt = Clock.System.now(),
                            finishedAt = null,
                            configuration = AnalyzerJobConfiguration(),
                            status = AnalyzerJobStatus.SCHEDULED,
                            repositoryUrl = "https://example.com/git/repository.git",
                            repositoryRevision = "main"
                        )
                    )
                )
            }
        }
    }
}

fun finishAnalyzerJob(json: Json): OpenApiRoute.() -> Unit = {
    operationId = "finishAnalyzerJob"
    summary = "Finish an analyzer job."
    tags = listOf("Jobs")

    request {
        pathParameter<Long>("organizationId") {
            description = "The analyzer job's ID."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success"
            jsonBody<AnalyzerJob> {
                example(
                    "Finish analyzer job",
                    json.encodeToString(
                        AnalyzerJob(
                            id = 1,
                            createdAt = Clock.System.now(),
                            startedAt = Clock.System.now(),
                            finishedAt = Clock.System.now(),
                            configuration = AnalyzerJobConfiguration(),
                            status = AnalyzerJobStatus.FINISHED,
                            repositoryUrl = "https://example.com/git/repository.git",
                            repositoryRevision = "main"
                        )
                    )
                )
            }
        }
    }
}
