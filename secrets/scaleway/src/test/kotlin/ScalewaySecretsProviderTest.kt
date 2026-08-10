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

package org.eclipse.apoapsis.ortserver.secrets.scaleway

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretValue

class ScalewaySecretsProviderTest : WordSpec({
    val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

    beforeSpec {
        server.start()
    }

    afterSpec {
        server.stop()
    }

    beforeEach {
        server.resetAll()
    }

    "createPath()" should {
        "create an absolute path from the path prefix and path name" {
            val provider = createProvider()
            val path = provider.createPath(OrganizationId(1), "This_is_a_29-chr._secret_name")
            path.path shouldBe "/organization_1/This_is_a_29-chr._secret_name"
            path.toScaleway() shouldBe Pair("/organization_1", "This_is_a_29-chr._secret_name")
        }
    }

    "readSecret()" should {
        "send the expected path parameters to Scaleway" {
            server.stubSecretAccess()

            val provider = createProvider(server.createConfig())
            val path = Path("/organization_1/This_is_a_29-chr._secret_name")

            provider.readSecret(path) shouldBe SecretValue("secret")

            server.verifySecretAccess("/organization_1", "This_is_a_29-chr._secret_name")
        }
    }

    "writeSecret()" should {
        "create a secret and a version if the secret does not exist" {
            server.stubSecretsList(totalCount = 0)
            server.stubSecretCreate()
            server.stubSecretVersionCreate()

            val provider = createProvider(server.createConfig())
            val path = Path("/organization_1/This_is_a_29-chr._secret_name")

            provider.writeSecret(path, SecretValue("secret"))

            server.verifySecretsList("/organization_1", "This_is_a_29-chr._secret_name")
            server.verifySecretCreate("/organization_1", "This_is_a_29-chr._secret_name")
            server.verifySecretVersionCreate()
        }

        "create a version if the secret already exists" {
            server.stubSecretsList(totalCount = 1)
            server.stubSecretVersionCreate(revision = 2)

            val provider = createProvider(server.createConfig())
            val path = Path("/organization_1/This_is_a_29-chr._secret_name")

            provider.writeSecret(path, SecretValue("secret"))

            server.verifySecretsList("/organization_1", "This_is_a_29-chr._secret_name")
            server.verify(0, postRequestedFor(urlPathEqualTo(SECRETS_ENDPOINT)))
            server.verifySecretVersionCreate()
        }
    }

    "removeSecret()" should {
        "delete the secret if it exists" {
            server.stubSecretsList(totalCount = 1)
            server.stubSecretDelete()

            val provider = createProvider(server.createConfig())
            val path = Path("/organization_1/This_is_a_29-chr._secret_name")

            provider.removeSecret(path)

            server.verifySecretsList("/organization_1", "This_is_a_29-chr._secret_name")
            server.verifySecretDelete()
        }

        "do nothing if the secret does not exist" {
            server.stubSecretsList(totalCount = 0)

            val provider = createProvider(server.createConfig())
            val path = Path("/organization_1/This_is_a_29-chr._secret_name")

            provider.removeSecret(path)

            server.verifySecretsList("/organization_1", "This_is_a_29-chr._secret_name")
            server.verifySecretDelete(count = 0)
        }
    }

    "A CRUD workflow" should {
        // These tests connect to the real production Scaleway API and are only enabled if credentials are provided via
        // environment variables.
        val config = createConfig(
            secretKey = System.getenv("SCW_SECRET_KEY").orEmpty(),
            projectId = System.getenv("SCW_PROJECT_ID").orEmpty()
        )
        val provider = createProvider(config)
        val path = provider.createPath(OrganizationId(1), "This_is_a_29-chr._secret_name")
        val secret = SecretValue("Ernie & Bert live at Sesame Street!")

        "not throw for 'removeSecret()' even if the secret does not exist".config(enabled = config.hasCredentials) {
            provider.removeSecret(path)
        }

        "return null for 'readSecret()' on a non-existing path".config(enabled = config.hasCredentials) {
            provider.readSecret(path) should beNull()
        }

        "not throw for 'writeSecret()'".config(enabled = config.hasCredentials) {
            provider.writeSecret(path, secret)
        }

        "return the secret for 'readSecret()'".config(enabled = config.hasCredentials) {
            provider.readSecret(path) shouldBe secret
        }

        "not throw for 'removeSecret()'".config(enabled = config.hasCredentials) {
            provider.removeSecret(path)
        }
    }
})

private fun createConfig(
    serverUrl: String = ScalewayConfiguration.DEFAULT_SERVER_URL,
    secretKey: String = "",
    projectId: String = ""
) = ScalewayConfiguration(
    serverUrl = serverUrl,
    secretKey = secretKey,
    projectId = projectId
)

private fun WireMockServer.createConfig() = createConfig(
    serverUrl = "http://localhost:${port()}",
    secretKey = "test-secret-key",
    projectId = "test-project-id"
)

private fun createProvider(config: ScalewayConfiguration = createConfig()) =
    ScalewaySecretsProvider(config)

private const val PATH_PREFIX = "/secret-manager/v1beta1/regions/fr-par"
private const val SECRETS_ENDPOINT = "$PATH_PREFIX/secrets"
private const val SECRETS_ACCESS_ENDPOINT = "$PATH_PREFIX/secrets-by-path/versions/latest/access"
private const val VERSIONS_ENDPOINT = "$SECRETS_ENDPOINT/secret-id/versions"
private const val DELETE_ENDPOINT = "$SECRETS_ENDPOINT/secret-id"

private fun WireMockServer.stubSecretAccess() {
    stubFor(
        get(urlPathEqualTo(SECRETS_ACCESS_ENDPOINT))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "secret_id": "secret-id",
                          "revision": 1,
                          "data": "c2VjcmV0"
                        }
                        """.trimIndent()
                    )
            )
    )
}

private fun WireMockServer.stubSecretsList(totalCount: Int) {
    val secrets = if (totalCount == 0) "" else SECRET_RESPONSE

    stubFor(
        get(urlPathEqualTo(SECRETS_ENDPOINT))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "secrets": [$secrets],
                          "total_count": $totalCount
                        }
                        """.trimIndent()
                    )
            )
    )
}

private fun WireMockServer.stubSecretCreate() {
    stubFor(
        post(urlPathEqualTo(SECRETS_ENDPOINT))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(SECRET_RESPONSE)
            )
    )
}

private fun WireMockServer.stubSecretVersionCreate(revision: Int = 1) {
    stubFor(
        post(urlPathEqualTo(VERSIONS_ENDPOINT))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {
                          "revision": $revision,
                          "secret_id": "secret-id",
                          "latest": true
                        }
                        """.trimIndent()
                    )
            )
    )
}

private fun WireMockServer.stubSecretDelete() {
    stubFor(
        delete(urlPathEqualTo(DELETE_ENDPOINT))
            .willReturn(aResponse().withStatus(204))
    )
}

private fun WireMockServer.verifySecretAccess(secretPath: String, secretName: String) {
    verify(
        getRequestedFor(urlPathEqualTo(SECRETS_ACCESS_ENDPOINT))
            .withHeader("X-Auth-Token", equalTo("test-secret-key"))
            .withQueryParam("project_id", equalTo("test-project-id"))
            .withQueryParam("secret_path", equalTo(secretPath))
            .withQueryParam("secret_name", equalTo(secretName))
    )
}

private fun WireMockServer.verifySecretsList(secretPath: String, secretName: String) {
    verify(
        getRequestedFor(urlPathEqualTo(SECRETS_ENDPOINT))
            .withQueryParam("project_id", equalTo("test-project-id"))
            .withQueryParam("path", equalTo(secretPath))
            .withQueryParam("name", equalTo(secretName))
    )
}

private fun WireMockServer.verifySecretCreate(
    secretPath: String,
    secretName: String
) {
    verify(
        postRequestedFor(urlPathEqualTo(SECRETS_ENDPOINT))
            .withRequestBody(matchingJsonPath("$.project_id", equalTo("test-project-id")))
            .withRequestBody(matchingJsonPath("$.path", equalTo(secretPath)))
            .withRequestBody(matchingJsonPath("$.name", equalTo(secretName)))
    )
}

private fun WireMockServer.verifySecretVersionCreate(count: Int = 1) {
    verify(
        count,
        postRequestedFor(urlPathEqualTo(VERSIONS_ENDPOINT))
            .withRequestBody(matchingJsonPath("$.data", equalTo("c2VjcmV0")))
    )
}

private fun WireMockServer.verifySecretDelete(count: Int = 1) {
    verify(
        count,
        deleteRequestedFor(urlPathEqualTo(DELETE_ENDPOINT))
    )
}

private val SECRET_RESPONSE =
    """
    {
      "id": "secret-id",
      "project_id": "test-project-id",
      "name": "This_is_a_29-chr._secret_name",
      "status": "ready",
      "created_at": "2026-01-01T00:00:00Z",
      "updated_at": "2026-01-01T00:00:00Z",
      "tags": [],
      "version_count": 1,
      "description": "",
      "managed": false,
      "protected": false,
      "type": "opaque",
      "path": "/organization_1",
      "used_by": [],
      "deletion_requested_at": null,
      "ephemeral_policy": null,
      "region": "fr-par"
    }
    """.trimIndent()
