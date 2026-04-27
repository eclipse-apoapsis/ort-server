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

package org.eclipse.apoapsis.ortserver.components.dependencygraph

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.api.v1.model.Identifier

@Serializable
data class DependencyGraphs(
    val graphs: Map<String, DependencyGraph>
)

@Serializable
data class DependencyGraph(
    val packages: List<Identifier>,
    val purls: List<String?>,
    val nodes: List<DependencyGraphNode>,
    val edges: List<DependencyGraphEdge>,
    val projectGroups: List<DependencyGraphProjectGroup>,
    val packageCount: Int?
)

@Serializable
data class DependencyGraphProjectGroup(
    val projectLabel: String,
    val scopes: List<DependencyGraphScope>,
    val packageCount: Int?
)

@Serializable
data class DependencyGraphScope(
    val scopeName: String,
    val scopeLabel: String?,
    val rootNodeIndexes: List<Int>,
    val packageCount: Int?
)

@Serializable
data class DependencyGraphNode(
    val pkg: Int,
    val fragment: Int,
    val linkage: String,
    val packageCount: Int
)

@Serializable
data class DependencyGraphEdge(
    val from: Int,
    val to: Int
)
