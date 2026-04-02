/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.dependencygraph.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.authorization.routes.AuthorizationChecker
import org.eclipse.apoapsis.ortserver.components.authorization.routes.get
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraph
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphEdge
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphNode
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphProjectGroup
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphScope
import org.eclipse.apoapsis.ortserver.components.dependencygraph.DependencyGraphs
import org.eclipse.apoapsis.ortserver.components.dependencygraph.backend.DependencyGraphService
import org.eclipse.apoapsis.ortserver.shared.ktorutils.jsonBody
import org.eclipse.apoapsis.ortserver.shared.ktorutils.requireIdParameter

internal fun Route.getRunDependencyGraph(service: DependencyGraphService, checker: AuthorizationChecker) =
    get({
        operationId = "getRunDependencyGraph"
        summary = "Get the dependency graphs for an ORT run"
        tags = listOf("Runs")

        request {
            pathParameter<Long>("runId") {
                description = "The ID of the ORT run."
            }
        }

        response {
            HttpStatusCode.OK to {
                description = "Success."
                jsonBody<DependencyGraphs> {
                    example("Get dependency graph") {
                        value = DependencyGraphs(
                            graphs = mapOf(
                                "Maven" to DependencyGraph(
                                    packages = listOf(
                                        org.eclipse.apoapsis.ortserver.api.v1.model.Identifier(
                                            type = "Maven",
                                            namespace = "com.example",
                                            name = "root",
                                            version = "1.0"
                                        )
                                    ),
                                    purls = listOf(null),
                                    packageCount = 1,
                                    nodes = listOf(
                                        DependencyGraphNode(
                                            pkg = 0,
                                            fragment = 0,
                                            linkage = "PROJECT_DYNAMIC",
                                            packageCount = 1
                                        )
                                    ),
                                    edges = listOf(
                                        DependencyGraphEdge(from = 0, to = 1)
                                    ),
                                    projectGroups = listOf(
                                        DependencyGraphProjectGroup(
                                            projectLabel = "Maven:com.example:root:1.0",
                                            packageCount = 1,
                                            scopes = listOf(
                                                DependencyGraphScope(
                                                    scopeName = "com.example:root:1.0:compile",
                                                    scopeLabel = "compile",
                                                    rootNodeIndexes = listOf(0),
                                                    packageCount = 1
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    }
                }
            }
        }
    }, checker) {
        call.respond(HttpStatusCode.OK, service.getDependencyGraphs(call.requireIdParameter("runId")))
    }
