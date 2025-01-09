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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.api.v1.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.api.v1.model.Jobs
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRun
import org.eclipse.apoapsis.ortserver.api.v1.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.client.OrtServerClientException
import org.eclipse.apoapsis.ortserver.client.api.RunsApi
import org.eclipse.apoapsis.ortserver.client.createOrtHttpClient

class RunsApiTest : StringSpec({
    "getOrtRun" should {
        "return an ORT run" {
            val respondOrtRun = OrtRun(
                id = 1,
                index = 1,
                organizationId = 1,
                productId = 1,
                repositoryId = 1,
                revision = "main",
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                jobConfigs = JobConfigurations(),
                status = OrtRunStatus.CREATED,
                jobs = Jobs(),
                issues = emptyList(),
                traceId = null,
                labels = emptyMap()
            )

            val mockEngine = MockEngine { jsonRespond(respondOrtRun, HttpStatusCode.OK) }
            val client = createOrtHttpClient(engine = mockEngine)

            val runsApi = RunsApi(client)

            runsApi.getOrtRun(1) shouldBe respondOrtRun
        }

        "throw an exception if the ORT run does not exist" {
            val mockEngine = MockEngine { respondError(HttpStatusCode.NotFound) }
            val client = createOrtHttpClient(engine = mockEngine)

            val runsApi = RunsApi(client)

            shouldThrow<OrtServerClientException> {
                runsApi.getOrtRun(1)
            }
        }
    }
})
