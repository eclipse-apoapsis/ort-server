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

package org.ossreviewtoolkit.server.core.api

import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import kotlinx.serialization.json.Json

import org.koin.ktor.ext.inject

import org.ossreviewtoolkit.server.api.v1.mapToApi
import org.ossreviewtoolkit.server.core.apiDocs.finishAnalyzerJob
import org.ossreviewtoolkit.server.core.apiDocs.getAnalyzerJobById
import org.ossreviewtoolkit.server.core.apiDocs.startAnalyzerJob
import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.model.AnalyzerJobStatus
import org.ossreviewtoolkit.server.orchestrator.OrchestratorService

fun Route.jobs() = route("jobs") {
    val orchestratorService by inject<OrchestratorService>()
    val json by inject<Json>()

    route("analyzer") {
        route("start") {
            post(startAnalyzerJob(json)) {
                val job = orchestratorService.startAnalyzerJob()

                if (job != null) {
                    call.respond(HttpStatusCode.OK, job.mapToApi())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }

        route("{id}") {
            get(getAnalyzerJobById(json)) {
                val id = call.requireParameter("id").toLong()

                val job = orchestratorService.getAnalyzerJob(id)

                if (job != null) {
                    call.respond(HttpStatusCode.OK, job.mapToApi())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            route("finish") {
                post(finishAnalyzerJob(json)) {
                    val id = call.requireParameter("id").toLong()

                    val job = orchestratorService.finishAnalyzerJob(id)

                    if (job != null) {
                        call.respond(HttpStatusCode.OK, job.mapToApi())
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }

            route("fail") {
                post {
                    val id = call.requireParameter("id").toLong()

                    val job = orchestratorService.updateAnalyzerJobStatus(id, AnalyzerJobStatus.FAILED)

                    if (job != null) {
                        call.respond(HttpStatusCode.OK, job.mapToApi())
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }
}
