/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.client.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun

/**
 * A client for the repositories API.
 */
class RepositoriesApi(
    /**
     * The configured HTTP client for the interaction with the API.
     */
    private val client: HttpClient
) {
    /**
     * Create a new [ORT run][ortRun] for the given [repository][repositoryId].
     */
    suspend fun createOrtRun(repositoryId: Long, ortRun: CreateOrtRun): OrtRun =
        client.post("api/v1/repositories/$repositoryId/runs") {
            setBody(ortRun)
        }.body()

    /**
     * Get the [ORT run][OrtRun] with the given [repositoryId] and [index].
     */
    suspend fun getOrtRun(repositoryId: Long, index: Long): OrtRun =
        client.get("api/v1/repositories/$repositoryId/runs/$index").body()
}
