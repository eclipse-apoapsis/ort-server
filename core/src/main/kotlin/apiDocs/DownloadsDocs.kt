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

package org.eclipse.apoapsis.ortserver.core.apiDocs

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute

import io.ktor.http.HttpStatusCode

val getReportByRunIdAndToken: OpenApiRoute.() -> Unit = {
    operationId = "GetReportByRunIdAndToken"
    summary = "Download a report of an ORT run using a token"
    description = "This endpoint does not require authentication."
    tags = listOf("Reports")

    request {
        pathParameter<Long>("runId") {
            description = "The ID of the ORT run."
        }
        pathParameter<String>("token") {
            description = "The token providing access to the report file to be downloaded."
        }
    }

    response {
        HttpStatusCode.OK to {
            description = "Success. The response body contains the requested report file."
            header<String>("Content-Type") {
                description = "The content type is set to the media type derived from the report file."
            }
        }

        HttpStatusCode.NotFound to {
            description = "The provided token could not be resolved or has expired."
        }
    }
}
