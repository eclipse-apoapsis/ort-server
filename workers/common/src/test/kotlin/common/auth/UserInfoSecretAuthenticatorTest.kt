/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.common.auth

import io.kotest.core.spec.style.StringSpec
import io.kotest.extensions.system.withEnvironment
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.URI

class UserInfoSecretAuthenticatorTest : StringSpec({
    "A URL with credentials defined in a 'URL' variable should be handled correctly" {
        testAuthentication("https://test.example.com:8080/test") shouldNotBeNull {
            userName shouldBe testSecrets[USERNAME_SECRET]
            String(password) shouldBe testSecrets[PASSWORD_SECRET]
        }
    }

    "A URL with credentials defined in a 'URI' variable should be handled correctly" {
        testAuthentication("https://test.example.com:8080/test") shouldNotBeNull {
            userName shouldBe testSecrets[USERNAME_SECRET]
            String(password) shouldBe testSecrets[PASSWORD_SECRET]
        }
    }

    "A RequestorType other than SERVER should be ignored" {
        testAuthentication("https://test.example.com:8080/test", Authenticator.RequestorType.PROXY) should beNull()
    }

    "A request for an unknown URL without credentials should return null" {
        testAuthentication("https://unknown.example.com/test") should beNull()
    }

    "An unresolvable username secret should be returned as is" {
        val url = "https://unknown-user.example.com:8080/test"

        testAuthentication(url) shouldNotBeNull {
            userName shouldBe "unknownUser"
            String(password) shouldBe testSecrets[PASSWORD_SECRET]
        }
    }

    "An unresolvable password secret should be returned as is" {
        val url = "https://unknown-pwd.example.com:8080/test"

        testAuthentication(url) shouldNotBeNull {
            userName shouldBe testSecrets[USERNAME_SECRET]
            String(password) shouldBe "unknownPwd"
        }
    }

    "A request without a URL should return null" {
        val authenticator = UserInfoSecretAuthenticator.create(testResolverFun)

        authenticator.requestPasswordAuthenticationInstance(
            "test.example.com",
            null,
            443,
            "https",
            "prompt",
            null,
            null,
            Authenticator.RequestorType.SERVER
        ) should beNull()
    }

    "A non-exact match for a URL should be performed" {
        val url = "https://test-match.example.com/test/sub/path/artifact.tar.gz"
        val env = mapOf(
            "TOP_LEVEL_URL" to "https://foo:bar@test-match.example.com/test",
            "SUB_LEVEL_URL" to "https://$USERNAME_SECRET:$PASSWORD_SECRET@test-match.example.com/test/sub/path"
        )

        testAuthentication(url, env = env) shouldNotBeNull {
            userName shouldBe testSecrets[USERNAME_SECRET]
            String(password) shouldBe testSecrets[PASSWORD_SECRET]
        }
    }
})

/** The name of the secret that represents the username. */
private const val USERNAME_SECRET = "username"

/** The name of the secret that represents the password. */
private const val PASSWORD_SECRET = "password"

/** A map containing test secrets used by authentication tests. */
private val testSecrets = mapOf(USERNAME_SECRET to "scott", PASSWORD_SECRET to "tiger")

/** A map containing some environment variables defining credentials for service URLs. */
private val testEnvironment = mapOf(
    "TEST_SERVICE_URL" to "https://$USERNAME_SECRET:$PASSWORD_SECRET@test.example.com:8080/test",
    "TEST_SERVICE_URI_VAR" to "https://$USERNAME_SECRET:$PASSWORD_SECRET@test2.example.com/test",
    "DIRECT_CREDENTIALS_url_VAR" to "https://${testSecrets[USERNAME_SECRET]}:${testSecrets[PASSWORD_SECRET]}@" +
            "direct.example.com/test",
    "UNKNOWN_USERNAME_URL" to "https://unknownUser:$PASSWORD_SECRET@unknown-user.example.com:8080/test",
    "UNKNOWN_PASSWORD_URL" to "https://$USERNAME_SECRET:unknownPwd@unknown-pwd.example.com:8080/test",
    "NOT_A_SERVICE_URL" to "something_completely_different",
    "NO_CREDENTIALS_URL" to "https://test.example.com/test",
    "FOO" to "bar"
)

/** A [InfraSecretResolverFun] that can resolve the secrets defined in the map with test secrets. */
private val testResolverFun: InfraSecretResolverFun = { secret ->
    testSecrets.getValue(secret.path)
}

/**
 * Create a test [UserInfoSecretAuthenticator] instance and invoke it for the given [url] and [requestorType]. Return
 * the resulting [PasswordAuthentication].
 */
private fun testAuthentication(
    url: String,
    requestorType: Authenticator.RequestorType = Authenticator.RequestorType.SERVER,
    env: Map<String, String> = testEnvironment
): PasswordAuthentication? =
    withEnvironment(env) {
        val authenticator = UserInfoSecretAuthenticator.create(testResolverFun)

        val authUrl = URI.create(url).toURL()
        authenticator.requestPasswordAuthenticationInstance(
            authUrl.host,
            null,
            authUrl.port,
            authUrl.protocol,
            null,
            null,
            authUrl,
            requestorType
        )
    }
