/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

import io.ktor.server.application.call
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

import org.koin.ktor.ext.inject

import org.ossreviewtoolkit.server.core.utils.requireParameter
import org.ossreviewtoolkit.server.services.ReportStorageService

/**
 * API for the runs endpoint. This endpoint provides information related to ORT runs and their results.
 */
fun Route.runs() = route("runs/{runId}") {
    route("reporter/{fileName}") {
        val reportStorageService by inject<ReportStorageService>()

        get {
            val runId = call.requireParameter("runId").toLong()
            val fileName = call.requireParameter("fileName")

            val downloadData = reportStorageService.fetchReport(runId, fileName)

            call.respondOutputStream(downloadData.contentType, producer = downloadData.loader)
        }
    }
}
