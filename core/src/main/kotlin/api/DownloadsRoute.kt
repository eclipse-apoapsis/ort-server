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

package org.eclipse.apoapsis.ortserver.core.api

import io.github.smiley4.ktoropenapi.get

import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

import org.eclipse.apoapsis.ortserver.core.apiDocs.getReportByRunIdAndToken
import org.eclipse.apoapsis.ortserver.core.utils.requireParameter
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.services.ReportStorageService

import org.koin.ktor.ext.inject

/**
 * A function defining routes for downloading artifacts for specific ORT runs. What makes these routes special is the
 * fact that they do not require authentication. Instead, the caller has to provide a secure token that identifies the
 * artifact to download.
 */
fun Route.downloads() = route("runs/{runId}/downloads") {
    val ortRunRepository by inject<OrtRunRepository>()

    route("report/{token}") {
        val reportStorageService by inject<ReportStorageService>()

        get(getReportByRunIdAndToken) {
            call.forRun(ortRunRepository) { ortRun ->
                val token = call.requireParameter("token")

                val downloadData = reportStorageService.fetchReportByToken(ortRun.id, token)

                call.respondOutputStream(downloadData.contentType, producer = downloadData.loader)
            }
        }
    }
}
