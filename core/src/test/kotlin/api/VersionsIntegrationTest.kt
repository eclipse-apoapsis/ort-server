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

import io.kotest.assertions.ktor.client.shouldHaveStatus

import io.ktor.client.request.get

import org.eclipse.apoapsis.ortserver.core.shouldHaveBody
import org.eclipse.apoapsis.ortserver.utils.system.ORT_SERVER_VERSION
import org.eclipse.apoapsis.ortserver.utils.test.Integration

import org.ossreviewtoolkit.utils.ort.Environment

class VersionsIntegrationTest : AbstractIntegrationTest({
    tags(Integration)

    "/versions" should {
        "respond with the correct versions" {
            integrationTestApplication {
                val response = testUserClient.get("/api/v1/versions")

                response shouldHaveStatus 200
                response shouldHaveBody mapOf(
                    "ORT Server" to ORT_SERVER_VERSION,
                    "ORT Core" to Environment.ORT_VERSION
                )
            }
        }

        "require authentication" {
            requestShouldRequireAuthentication {
                get("/api/v1/versions")
            }
        }
    }
})
