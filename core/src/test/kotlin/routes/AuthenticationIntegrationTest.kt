/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.common.runBlocking
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication

import java.io.File
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Base64
import java.util.Date

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.ossreviewtoolkit.server.core.plugins.configureAuthentication
import org.ossreviewtoolkit.server.core.plugins.configureHTTP
import org.ossreviewtoolkit.server.core.plugins.configureKoin
import org.ossreviewtoolkit.server.core.plugins.configureRouting
import org.ossreviewtoolkit.server.core.plugins.configureSerialization
import org.ossreviewtoolkit.server.core.plugins.configureStatusPages

private const val CERT_STORE = "testkeycloak.jks"
private const val CERT_ENTRY = "testkeycloak"
private const val CERT_PASSWORD = "testkeycloak"
private const val CERT_ISSUER = "https://testkeycloak.example.org"
private const val JWKS_ENDPOINT = "/auth/realms/master/protocol/openid-connect/certs"
private const val KEY_ID = "testKey"

class AuthenticationIntegrationTest : FunSpec() {
    private val issuerData = loadIssuerData()

    private val server = WireMockServer(
        WireMockConfiguration.options()
            .dynamicPort()
    )

    override suspend fun beforeSpec(spec: Spec) {
        server.start()
        server.stubJwks(issuerData)
    }

    override suspend fun afterSpec(spec: Spec) {
        server.stop()
    }

    init {
        test("A request with a valid token is accepted.") {
            withTestORTServer {
                val response = it.get("/api/v1/organizations/0") {
                    headers {
                        token {
                            withIssuer(CERT_ISSUER)
                            withAudience("ort-server")
                        }
                    }
                }
                response shouldHaveStatus HttpStatusCode.NotFound
            }
        }

        test("A request without a token is rejected.") {
            withTestORTServer {
                val response = it.get("/api/v1/organizations")
                response shouldHaveStatus HttpStatusCode.Unauthorized
            }
        }

        test("A request with an expired token is rejected.") {
            withTestORTServer {
                val cal = LocalDateTime.now().minusHours(2).atZone(ZoneId.systemDefault())
                val response = it.get("/api/v1/organizations/0") {
                    headers {
                        token {
                            withIssuer(CERT_ISSUER)
                            withAudience("ort-server")
                            withExpiresAt(Date.from(cal.toInstant()))
                        }
                    }
                }
                response shouldHaveStatus HttpStatusCode.Unauthorized
            }
        }

        test("A token without an audience claim is rejected.") {
            withTestORTServer {
                val response = it.get("/api/v1/organizations/0") {
                    headers {
                        token {
                            withIssuer(CERT_ISSUER)
                        }
                    }
                }
                response shouldHaveStatus HttpStatusCode.Unauthorized
            }
        }

        test("A token with a wrong audience claim is rejected.") {
            withTestORTServer {
                val response = it.get("/api/v1/organizations/0") {
                    headers {
                        token {
                            withIssuer(CERT_ISSUER)
                            withAudience("some", "other", "audiences")
                        }
                    }
                }
                response shouldHaveStatus HttpStatusCode.Unauthorized
            }
        }
    }

    /**
     * Execute the [test] block on a properly configured test application instance.
     */
    private fun withTestORTServer(test: suspend (HttpClient) -> Unit) = testApplication {
        environment {
            config = MapApplicationConfig().also { it.initTestJwtConfig() }
        }

        application {
            configureKoin()
            configureAuthentication()
            configureStatusPages()
            configureRouting()
            configureSerialization()
            configureHTTP()
        }

        runBlocking {
            test(client)
        }
    }

    /**
     *
     * Create a token using the given [issuerData] that can be configured with [builder].
     */
    private fun createToken(builder: JWTCreator.Builder.() -> JWTCreator.Builder): String =
        builder.invoke(JWT.create()).sign(Algorithm.RSA256(issuerData.publicKey, issuerData.privateKey))

    /**
     * Add an Authorization header with a bearer token as configured by [builder] to this test request.
     */
    private fun HeadersBuilder.token(builder: JWTCreator.Builder.() -> JWTCreator.Builder) {
        set(HttpHeaders.Authorization, "Bearer ${createToken(builder)}")
    }

    /**
     * Override the properties related to JWT validation with test values. Note: It seems to be necessary to override
     * all the properties in the jwt sub-path; otherwise, the missing ones are not found.
     */
    private fun MapApplicationConfig.initTestJwtConfig() {
        put("jwt.jwksUri", "http://localhost:${server.port()}$JWKS_ENDPOINT")
        put("jwt.issuer", CERT_ISSUER)
        put("jwt.realm", "master")
        put("jwt.audience", "ort-server")
    }
}

/**
 * Load the data about the fake token issuer from the key store shipped with the test.
 */
private fun loadIssuerData(): IssuerData {
    val certFile = File("src/test/resources/$CERT_STORE")
    val keyStore = KeyStore.getInstance("JKS").apply {
        certFile.inputStream().use {
            load(it, CERT_PASSWORD.toCharArray())
        }
    }

    val protectionParam = KeyStore.PasswordProtection(CERT_PASSWORD.toCharArray())
    val entry = keyStore.getEntry(CERT_ENTRY, protectionParam) as KeyStore.PrivateKeyEntry
    val publicKey = entry.certificate.publicKey as RSAPublicKey
    val privateKey = entry.privateKey as RSAPrivateKey

    return IssuerData(entry.certificate, publicKey, privateKey)
}

/**
 * Prepare this mock server to serve requests for a JWKS document. Generate the content of this document based on the
 * provided [issuerData].
 */
private fun WireMockServer.stubJwks(issuerData: IssuerData) {
    val jwks = Jwks(
        kid = KEY_ID,
        alg = "RS256",
        kty = "RSA",
        x5c = listOf(encode(issuerData.certificate.encoded)),
        n = encode(issuerData.publicKey.modulus.toByteArray()),
        e = encode(issuerData.publicKey.publicExponent.toByteArray())
    )
    val keys = mapOf("keys" to listOf(jwks))
    val jwksStr = Json.encodeToString(keys)

    stubFor(
        WireMock.get(WireMock.urlPathEqualTo(JWKS_ENDPOINT))
            .willReturn(
                WireMock.aResponse().withStatus(HttpStatusCode.OK.value)
                    .withBody(jwksStr)
            )
    )
}

/**
 * Convert the given byte array to a base64-encoded string.
 */
private fun encode(data: ByteArray): String = Base64.getUrlEncoder().encodeToString(data)

/**
 * A data class to represent the JSON data served by a JWKS endpoint.
 */
@Serializable
private data class Jwks(
    val alg: String,
    val kid: String,
    val kty: String,
    val x5c: List<String>,
    val e: String,
    val n: String
)

/**
 * A data class holding information about the dummy token issuer. The fields are populated from the JKS stored under
 * test resources and then used to generate tokens.
 */
private data class IssuerData(
    /** The certificate of the simulated token issuer. */
    val certificate: Certificate,

    /** The public key of the issuer. */
    val publicKey: RSAPublicKey,

    /** The private key of the issuer. */
    val privateKey: RSAPrivateKey
)
