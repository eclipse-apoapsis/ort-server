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

package org.eclipse.apoapsis.ortserver.config.github

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.UrlPattern

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.ktor.http.HttpStatusCode

import io.mockk.every
import io.mockk.mockk

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProvider
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.DEFAULT_BRANCH
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.GITHUB_API_URL
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.JSON_CONTENT_TYPE_HEADER
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.RAW_CONTENT_TYPE_HEADER
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.REPOSITORY_NAME
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.REPOSITORY_OWNER
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.TOKEN

class GitHubConfigFileProviderTest : WordSpec({
    beforeSpec {
        server.start()
    }

    afterSpec {
        server.stop()
    }

    beforeEach {
        server.resetAll()
    }

    "resolveContext" should {
        "resolve a context successfully" {
            server.stubExistingRevision()

            val provider = getProvider()

            val resolvedContext = provider.resolveContext(Context(REVISION))
            resolvedContext.name shouldBe "0a4721665650ba7143871b22ef878e5b81c8f8b5"
        }

        "resolve the default context successfully" {
            server.stubExistingRevision()

            val provider = getProvider()

            val resolvedContext = provider.resolveContext(ConfigManager.DEFAULT_CONTEXT)

            resolvedContext.name shouldBe "0a4721665650ba7143871b22ef878e5b81c8f8b5"
        }

        "throw exception if response doesn't contain SHA-1 commit ID" {
            server.stubMissingRevision()

            val provider = getProvider()

            val exception = shouldThrow<NoSuchFieldException> {
                provider.resolveContext(Context(REVISION + 1))
            }

            exception.message shouldContain "SHA-1"
            exception.message shouldContain "commit"
            exception.message shouldContain "branch"
            exception.message shouldContain REVISION + 1
        }

        "throw exception if the branch does not exist" {
            server.stubRevisionNotFound()

            val provider = getProvider()

            val exception = shouldThrow<ConfigException> {
                provider.resolveContext(Context(REVISION + NOT_FOUND))
            }

            exception.message shouldContain "branch"
            exception.message shouldContain REVISION + NOT_FOUND
            exception.message shouldContain "repository"
            exception.message shouldContain REPOSITORY
        }
    }

    "getFile" should {
        "successfully retrieve a file" {
            server.stubRawFile()

            val provider = getProvider()

            val fileContent = provider.getFile(Context(REVISION), Path(CONFIG_PATH))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            fileContent shouldBe CONTENT
        }

        "throw an exception if the response has a wrong content type" {
            server.stubUnexpectedJsonContentType()

            val provider = getProvider()

            val exception = shouldThrow<ConfigException> {
                provider.getFile(Context(REVISION), Path(CONFIG_PATH + 1))
            }

            exception.message shouldContain "content type"
            exception.message shouldContain "application/json"
        }

        "throw an exception if the path refers a directory" {
            server.stubDirectory()

            val provider = getProvider()

            val exception = shouldThrow<ConfigException> {
                provider.getFile(Context(REVISION), Path(DIRECTORY_PATH))
            }

            exception.message shouldContain DIRECTORY_PATH
            exception.message shouldContain "directory"
        }
    }

    "contains" should {
        "return `true` if the config file is present" {
            server.stubJsonFileContentType()

            val provider = getProvider()

            provider.contains(Context(REVISION + 1), Path(CONFIG_PATH)) shouldBe true
        }

        "return `false` if the path refers a directory" {
            server.stubDirectory()

            val provider = getProvider()

            provider.contains(Context(REVISION), Path(DIRECTORY_PATH)) shouldBe false
        }

        "return `false` if a `NotFound` response is received" {
            server.stubFileNotFound()

            val provider = getProvider()

            provider.contains(Context(REVISION), Path(CONFIG_PATH + NOT_FOUND)) shouldBe false
        }
    }

    "listFiles" should {
        "return a list of files inside a given directory" {
            server.stubDirectory()

            val expectedPaths = setOf(Path(CONFIG_PATH + "1"), Path(CONFIG_PATH + "2"))

            val provider = getProvider()

            val listFiles = provider.listFiles(Context(REVISION), Path(DIRECTORY_PATH))

            listFiles shouldContainExactlyInAnyOrder expectedPaths
        }

        "throw an exception if the path does not refer a directory" {
            server.stubJsonFileContentType()

            val provider = getProvider()

            val exception = shouldThrow<ConfigException> {
                provider.listFiles(Context(REVISION + 1), Path(CONFIG_PATH))
            }

            exception.message shouldContain "`$CONFIG_PATH`"
            exception.message shouldContain "does not refer a directory."
        }
    }
})

/** A mock for GitHub API to be used by tests. */
private val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

internal const val OWNER = "owner"
internal const val REPOSITORY = "repository"
internal const val REVISION = "configs-branch"
internal const val CONFIG_PATH = "config/app.config"
internal const val DIRECTORY_PATH = "config"
internal const val CONTENT = "repository: repo, username: user, password: pass"
internal const val NOT_FOUND = "NotFound"
internal const val API_TOKEN = "test-api-token"

/**
 * Returns a configured [GitHubConfigFileProvider] instance.
 */
private fun getProvider(): GitHubConfigFileProvider {
    val secretProvider = mockk<ConfigSecretProvider>()

    every { secretProvider.getSecret(TOKEN) } returns API_TOKEN

    val config = ConfigFactory.parseMap(
        mapOf(
            GITHUB_API_URL to server.baseUrl(),
            REPOSITORY_OWNER to OWNER,
            REPOSITORY_NAME to REPOSITORY,
            DEFAULT_BRANCH to REVISION
        )
    )

    return GitHubConfigFileProvider.create(config, secretProvider)
}

/**
 * Prepare stubbing of a GET request with the expected authorization header.
 */
private fun authorizedGet(pattern: UrlPattern): MappingBuilder =
    get(pattern).withHeader("Authorization", equalTo("Bearer $API_TOKEN"))

/**
 * A stub for successfully resolving a revision.
 */
private fun WireMockServer.stubExistingRevision() {
    stubFor(
        authorizedGet(
            urlEqualTo("/repos/$OWNER/$REPOSITORY/branches/$REVISION")
        ).willReturn(
            okJson(
                """
                    {
                      "name": "app.config",
                      "commit": {
                        "sha": "0a4721665650ba7143871b22ef878e5b81c8f8b5",
                        "url": "https://www.example.org/some-commit-url"
                      }
                    }
                """.trimIndent()
            )
        )
    )
}

/**
 * A stub with a missing SHA-1 commit ID.
 */
private fun WireMockServer.stubMissingRevision() {
    stubFor(
        authorizedGet(
            urlEqualTo("/repos/$OWNER/$REPOSITORY/branches/${REVISION + 1}")
        ).willReturn(
            okJson(
                """
                    {
                      "name": "app.config",
                      "path": "$CONFIG_PATH",
                      "size": 303
                    }
                """.trimIndent()
            )
        )
    )
}

/**
 * A stub which returns a "Not Found" response for a revision.
 */
private fun WireMockServer.stubRevisionNotFound() {
    stubFor(
        authorizedGet(
            urlEqualTo("/repos/$OWNER/$REPOSITORY/branches/${REVISION + NOT_FOUND}")
        ).willReturn(
            status(
                HttpStatusCode.NotFound.value
            ).withBody(
                """
                    {
                      "message": "Branch not found",
                      "documentation_url": "https://docs.github.com/"
                    }
                """.trimIndent()
            )
        )
    )
}

/**
 * A stub for successfully getting a raw file.
 */
private fun WireMockServer.stubRawFile() {
    stubFor(
        authorizedGet(
            urlPathEqualTo("/repos/$OWNER/$REPOSITORY/contents/$CONFIG_PATH")
        ).withQueryParam("ref", equalTo(REVISION))
            .withHeader("Accept", equalTo(RAW_CONTENT_TYPE_HEADER))
            .willReturn(
                ok(CONTENT).withHeader("Content-Type", RAW_CONTENT_TYPE_HEADER)
            )
    )
}

/**
 * A stub which returns a "Not Found" response for a file.
 */
private fun WireMockServer.stubFileNotFound() {
    stubFor(
        authorizedGet(
            urlPathEqualTo("/repos/$OWNER/$REPOSITORY/contents/${CONFIG_PATH + NOT_FOUND}")
        ).willReturn(
            status(
                HttpStatusCode.NotFound.value
            ).withBody(
                """
                    {
                      "message": "Not Found",
                      "documentation_url": "https://docs.github.com/"
                    }
                """.trimIndent()
            )
        )
    )
}

/**
 * A stub returning a response with unexpected json content type.
 */
private fun WireMockServer.stubUnexpectedJsonContentType() {
    stubFor(
        authorizedGet(
            urlPathEqualTo("/repos/$OWNER/$REPOSITORY/contents/${CONFIG_PATH + 1}")
        ).withQueryParam("ref", equalTo(REVISION))
            .willReturn(
                aResponse().withBody(
                    "{\n" +
                            "    \"name\": \"app.config\",\n" +
                            "    \"path\": \"${CONFIG_PATH + 1}\",\n" +
                            "    \"sha\": \"0a4721665650ba7143871b22ef878e5b81c8f8b5\",\n" +
                            "    \"type\": \"file\",\n" +
                            "    \"size\": 303\n" +
                            "}"
                ).withHeader("Content-Type", JSON_CONTENT_TYPE_HEADER)
            )
    )
}

/**
 * A stub for successfully getting a file information in JSON format.
 */
@OptIn(ExperimentalEncodingApi::class)
private fun WireMockServer.stubJsonFileContentType() {
    stubFor(
        authorizedGet(
            urlPathEqualTo("/repos/$OWNER/$REPOSITORY/contents/$CONFIG_PATH")
        ).withQueryParam("ref", equalTo(REVISION + 1))
            .willReturn(
                okJson(
                    "{\n" +
                            "    \"name\": \"app.config\",\n" +
                            "    \"path\": \"$CONFIG_PATH\",\n" +
                            "    \"sha\": \"0a4721665650ba7143871b22ef878e5b81c8f8b5\",\n" +
                            "    \"size\": 303,\n" +
                            "    \"type\": \"file\",\n" +
                            "    \"content\": \"${Base64.encode(CONTENT.toByteArray())}\"\n" +
                            "}"
                )
            )
    )
}

/**
 * A stub returning a response for a path referencing a directory with several objects.
 */
private fun WireMockServer.stubDirectory() {
    stubFor(
        authorizedGet(
            urlPathEqualTo("/repos/$OWNER/$REPOSITORY/contents/$DIRECTORY_PATH")
        ).withQueryParam("ref", equalTo(REVISION))
            .willReturn(
                okJson(
                    "[\n" +
                            "{\n" +
                            "    \"name\": \"app1.config\",\n" +
                            "    \"path\": \"${CONFIG_PATH + 1}\",\n" +
                            "    \"sha\": \"0a4721665650ba7143871b22ef878e5b81c8f8b6\",\n" +
                            "    \"type\": \"file\",\n" +
                            "    \"size\": 908\n" +
                            "}," +
                            "{\n" +
                            "    \"name\": \"app2.config\",\n" +
                            "    \"path\": \"${CONFIG_PATH + 2}\",\n" +
                            "    \"sha\": \"0a4721665650ba7143871b22ef878e5b81c8f8b7\",\n" +
                            "    \"type\": \"file\",\n" +
                            "    \"size\": 305\n" +
                            "}," +
                            "{\n" +
                            "    \"name\": \"other configs\",\n" +
                            "    \"path\": \"${DIRECTORY_PATH + 1}\",\n" +
                            "    \"sha\": \"0a4721665650ba7143871b22ef878e5b81c8f8b5\",\n" +
                            "    \"type\": \"dir\",\n" +
                            "    \"size\": 303\n" +
                            "}\n" +
                            "]"
                )
            )
    )
}
