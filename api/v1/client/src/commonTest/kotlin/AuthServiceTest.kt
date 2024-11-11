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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

import org.eclipse.apoapsis.ortserver.client.auth.AuthService
import org.eclipse.apoapsis.ortserver.client.auth.AuthenticationException
import org.eclipse.apoapsis.ortserver.client.auth.TokenInfo

class AuthServiceTest : StringSpec({
    val tokenUrl = "http://localhost/token"
    val clientId = "test-client-id"

    "generateToken" should {
        "return a token" {
            val expectedToken = TokenInfo(
                accessToken = "test-access-token",
                refreshToken = "test-refresh-token",
                expiresInSeconds = 60
            )

            val mockEngine = MockEngine { respondToken(expectedToken) }
            val authService = AuthService(createHttpClientMock(mockEngine), tokenUrl, clientId)

            authService.generateToken("testUser", "testPassword") shouldBe expectedToken
        }

        "fail with an exception if a token cannot be generated" {
            val mockEngine = MockEngine { invalidRespond() }
            val authService = AuthService(createHttpClientMock(mockEngine), tokenUrl, clientId)

            shouldThrow<AuthenticationException> {
                authService.generateToken("testUser", "testPassword")
            }
        }
    }

    "refreshToken" should {
        "return a refreshed token" {
            val expectedToken = TokenInfo(
                accessToken = "test-access-token",
                refreshToken = "test-refresh-token",
                expiresInSeconds = 60
            )

            val mockEngine = MockEngine { respondToken(expectedToken) }
            val authService = AuthService(createHttpClientMock(mockEngine), tokenUrl, clientId)

            authService.refreshToken("test-refresh-token") shouldBe expectedToken
        }

        "fail with an exception if a token cannot be refreshed" {
            val mockEngine = MockEngine { invalidRespond() }
            val authService = AuthService(createHttpClientMock(mockEngine), tokenUrl, clientId)

            shouldThrow<AuthenticationException> {
                authService.refreshToken("test-refresh-token")
            }
        }
    }
})

private fun createHttpClientMock(mockEngine: MockEngine) = HttpClient(mockEngine) {
    install(ContentNegotiation) {
        json()
    }
}

private fun MockRequestHandleScope.invalidRespond() =
    respond(
        content = """
            {
                "error": "invalid_request",
                "error_description": "Invalid request."
            }
        """.trimIndent(),
        status = HttpStatusCode.Unauthorized,
        headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
    )

private fun MockRequestHandleScope.respondToken(token: TokenInfo) =
    respond(
        content = """
            {
                "access_token": "${token.accessToken}",
                "refresh_token": "${token.refreshToken}",
                "expires_in": ${token.expiresInSeconds}
            }
        """.trimIndent(),
        status = HttpStatusCode.OK,
        headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
    )
