/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.secrets.vault

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.StringSpec

import org.eclipse.apoapsis.ortserver.secrets.Path
import org.eclipse.apoapsis.ortserver.secrets.SecretValue
import org.eclipse.apoapsis.ortserver.secrets.vault.model.VaultCredentials

/**
 * A test class for [VaultSecretsProvider] that tests specific requests against the Vault API which are not supported
 * by the Vault test-containers implementation. Therefore, a mock server is used here.
 */
class VaultSecretsProviderRequestsTest : StringSpec({
    val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

    beforeSpec {
        server.start()
    }

    afterSpec {
        server.stop()
    }

    beforeEach {
        server.prepare()
    }

    "The prefix path can be configured" {
        val prefix = "customPrefix"
        val path = "to/my/secret"
        val config = VaultConfiguration(server.vaultUrl(), credentials, "", prefix = prefix)
        val provider = VaultSecretsProvider(config)

        provider.writeSecret(Path(path), SecretValue("secret"))

        server.verify(postRequestedFor(urlPathEqualTo("/v1/$prefix/data/$path")))
    }

    "The namespace can be configured" {
        val namespace = "custom/namespace"
        val path = "my/secret/in/namespace"
        val config = VaultConfiguration(server.vaultUrl(), credentials, "", namespace = namespace)
        val provider = VaultSecretsProvider(config)

        provider.writeSecret(Path(path), SecretValue("secretInNamespace"))

        server.verify(
            postRequestedFor(urlPathEqualTo("/v1/secret/data/$path"))
                .withHeader("X-Vault-Namespace", equalTo(namespace))
        )
    }
})

/** Credentials to log in into the Vault server. */
private val credentials = VaultCredentials("someRole", "someSecret")

/**
 * Return the URL of this mock server to be used in the [VaultConfiguration].
 */
private fun WireMockServer.vaultUrl() = "http://localhost:${port()}"

/**
 * Prepare this server for the execution of a test. This includes expecting a login request. Authentication is not
 * tested here, thus an arbitrary token is returned. All other requests just return a success result. The test
 * cases use _verify()_ to test whether the expected requests were sent.
 */
private fun WireMockServer.prepare() {
    resetAll()

    stubFor(
        post(anyUrl())
            .willReturn(aResponse().withStatus(200))
    )

    stubFor(
        post(urlPathEqualTo("/v1/auth/approle/login"))
            .willReturn(
                aResponse().withBody(
                    """
                    {
                      "auth": {
                        "client_token": "someToken"
                      }
                    }
                    """.trimIndent()
                )
                    .withHeader("Content-Type", "application/json")
            )
    )
}
