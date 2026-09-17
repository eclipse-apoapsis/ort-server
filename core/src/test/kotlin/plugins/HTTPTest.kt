/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

import kotlinx.serialization.json.Json

import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

private val responseMessage = "A compressible API response. ".repeat(20)
private val jsonResponse = "{\"message\":\"$responseMessage\"}"

class HTTPTest : WordSpec({
    afterEach {
        stopKoin()
    }

    "configureHTTP()" should {
        "compress JSON responses with gzip when requested" {
            testApplication {
                configureHttpTestApplication()

                val response = client.get("/json") {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                }

                response.status shouldBe HttpStatusCode.OK
                response.headers[HttpHeaders.ContentEncoding] shouldBe "gzip"
                GZIPInputStream(response.body<ByteArray>().inputStream()).use {
                    it.readBytes().decodeToString() shouldBe jsonResponse
                }
            }
        }

        "retain the default deflate encoder" {
            testApplication {
                configureHttpTestApplication()

                val response = client.get("/json") {
                    header(HttpHeaders.AcceptEncoding, "deflate")
                }

                response.headers[HttpHeaders.ContentEncoding] shouldBe "deflate"
                // Ktor's deflate encoder produces a raw DEFLATE stream without a zlib header.
                val inflater = Inflater(true)
                try {
                    InflaterInputStream(response.body<ByteArray>().inputStream(), inflater).use {
                        it.readBytes().decodeToString() shouldBe jsonResponse
                    }
                } finally {
                    inflater.end()
                }
            }
        }

        "leave responses uncompressed without Accept-Encoding" {
            testApplication {
                configureHttpTestApplication()

                val response = client.get("/json")

                response.headers[HttpHeaders.ContentEncoding].shouldBeNull()
                response.bodyAsText() shouldBe jsonResponse
            }
        }

        "leave small responses uncompressed" {
            testApplication {
                configureHttpTestApplication()

                val response = client.get("/small") {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                }

                response.headers[HttpHeaders.ContentEncoding].shouldBeNull()
                response.bodyAsText() shouldBe "small"
            }
        }

        "leave excluded content types uncompressed" {
            testApplication {
                configureHttpTestApplication()

                val response = client.get("/image") {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                }

                response.headers[HttpHeaders.ContentEncoding].shouldBeNull()
                response.body<ByteArray>() shouldBe responseMessage.encodeToByteArray()
            }
        }

        "compress streamed responses with unknown length" {
            testApplication {
                configureHttpTestApplication()

                val response = client.get("/stream") {
                    header(HttpHeaders.AcceptEncoding, "gzip")
                }

                response.headers[HttpHeaders.ContentEncoding] shouldBe "gzip"
                GZIPInputStream(response.body<ByteArray>().inputStream()).use {
                    it.readBytes().decodeToString() shouldBe responseMessage
                }
            }
        }

        "not decompress request bodies" {
            testApplication {
                configureHttpTestApplication()
                val compressedRequest = ByteArrayOutputStream().apply {
                    GZIPOutputStream(this).use { it.write(responseMessage.encodeToByteArray()) }
                }.toByteArray()

                val response = client.post("/echo") {
                    header(HttpHeaders.ContentEncoding, "gzip")
                    setBody(compressedRequest)
                }

                response.status shouldBe HttpStatusCode.OK
                response.body<ByteArray>() shouldBe compressedRequest
            }
        }
    }
})

private fun ApplicationTestBuilder.configureHttpTestApplication() {
    application {
        install(Koin) {
            modules(
                module {
                    single<ApplicationConfig> { MapApplicationConfig("ktor.cors.allowedHosts" to "localhost") }
                    single<Json> { Json }
                }
            )
        }

        configureHTTP()
        configureSerialization()

        routing {
            get("/json") {
                call.respond(mapOf("message" to responseMessage))
            }
            get("/small") {
                call.respondText("small")
            }
            get("/image") {
                call.respondBytes(responseMessage.encodeToByteArray(), ContentType.Image.PNG)
            }
            get("/stream") {
                call.respondOutputStream(ContentType.Text.Plain) {
                    write(responseMessage.encodeToByteArray())
                }
            }
            post("/echo") {
                call.respondBytes(call.receive<ByteArray>())
            }
        }
    }
}
