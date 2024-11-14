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
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.matching.UrlPattern
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.file.aDirectory
import io.kotest.matchers.file.exist
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import io.ktor.http.HttpStatusCode

import io.mockk.every
import io.mockk.mockk

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProvider
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.CACHE_CLEANUP_RATIO
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.CACHE_DIRECTORY
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.CACHE_MAX_AGE_DAYS
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.DEFAULT_BRANCH
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.GITHUB_API_URL
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.JSON_CONTENT_TYPE_HEADER
import org.eclipse.apoapsis.ortserver.config.github.GitHubConfigFileProvider.Companion.LOCK_CHECK_INTERVAL_SEC
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

        "resolve the empty context successfully" {
            server.stubExistingRevision()

            val provider = getProvider()

            val resolvedContext = provider.resolveContext(ConfigManager.EMPTY_CONTEXT)

            resolvedContext.name shouldBe "0a4721665650ba7143871b22ef878e5b81c8f8b5"
        }

        "fall back to the remote default branch" {
            server.stubDefaultBranch()
            server.stubExistingRevision()

            val provider = getProvider(DEFAULT_BRANCH to "")

            val resolvedContext = provider.resolveContext(ConfigManager.EMPTY_CONTEXT)

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

            exception.message shouldContain "404"
        }

        "clean up the cache" {
            server.stubExistingRevision()

            val cacheDir = tempdir()
            val outdatedModifiedTime = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 2

            val oldRevisionDir = cacheDir.resolve("old-revision").apply {
                mkdir()
                setLastModified(outdatedModifiedTime)
            }

            val currentRevisionDir = cacheDir.resolve(REVISION_HASH).apply {
                mkdir()
                setLastModified(outdatedModifiedTime)
            }

            val provider = getProvider(CACHE_DIRECTORY to cacheDir.absolutePath)
            provider.resolveContext(Context(REVISION))

            oldRevisionDir shouldNot exist()
            currentRevisionDir shouldBe aDirectory()
        }

        "only clean up the cache when the default branch is processed" {
            val revision = "other-branch"
            server.stubExistingRevision(revision)

            val cacheDir = tempdir()
            val outdatedModifiedTime = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 2

            val oldRevisionDir = cacheDir.resolve("old-revision").apply {
                mkdir()
                setLastModified(outdatedModifiedTime)
            }

            val provider = getProvider(CACHE_DIRECTORY to cacheDir.absolutePath)
            provider.resolveContext(Context(revision))

            oldRevisionDir shouldBe aDirectory()
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

        "use caching when configured" {
            server.stubRawFile()
            val cacheDir = tempdir()

            val provider = getProvider(CACHE_DIRECTORY to cacheDir.absolutePath)
            provider.getFile(Context(REVISION), Path(CONFIG_PATH)).close()

            val fileContent = provider.getFile(Context(REVISION), Path(CONFIG_PATH))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            fileContent shouldBe CONTENT

            server.allServeEvents shouldHaveSize 1
            val revisionCacheDir = cacheDir.resolve(REVISION)
            revisionCacheDir shouldBe aDirectory()
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

        "throw an exception if the response is not successful" {
            server.stubFor(
                get(anyUrl())
                    .willReturn(
                        aResponse()
                            .withStatus(HttpStatusCode.Unauthorized.value)
                            .withBody(CONTENT)
                            .withHeader("Content-Type", RAW_CONTENT_TYPE_HEADER)
                    )
            )

            val provider = getProvider()

            val exception = shouldThrow<ConfigException> {
                provider.getFile(Context(REVISION), Path(CONFIG_PATH + 1))
            }

            exception.message shouldContain "401"
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

        "use caching when configured" {
            server.stubDirectory()

            val expectedPaths = setOf(Path(CONFIG_PATH + "1"), Path(CONFIG_PATH + "2"))

            val cacheDir = tempdir()

            val provider = getProvider(CACHE_DIRECTORY to cacheDir.absolutePath)
            provider.listFiles(Context(REVISION), Path(DIRECTORY_PATH))

            val listFiles = provider.listFiles(Context(REVISION), Path(DIRECTORY_PATH))

            listFiles shouldContainExactlyInAnyOrder expectedPaths

            server.allServeEvents shouldHaveSize 1
            val revisionCacheDir = cacheDir.resolve(REVISION)
            revisionCacheDir shouldBe aDirectory()
        }
    }

    "GitHub rate limits" should {
        "be handled by a retry" {
            val scenario = "rateLimits"
            val stateRetry = "retry"
            val now = Clock.System.now().epochSeconds
            val retryTime = now + 1

            server.stubFor(
                authorizedGet(
                    urlEqualTo("/repos/$OWNER/$REPOSITORY/branches/$REVISION")
                ).inScenario(scenario)
                    .whenScenarioStateIs(STARTED)
                    .willReturn(
                        status(HttpStatusCode.Forbidden.value)
                            .withHeader("x-ratelimit-remaining", "0")
                            .withHeader("x-ratelimit-reset", (retryTime).toString())
                    )
                    .willSetStateTo(stateRetry)
            )
            server.stubFor(
                authorizedGet(
                    urlEqualTo("/repos/$OWNER/$REPOSITORY/branches/$REVISION")
                ).inScenario(scenario)
                    .whenScenarioStateIs(stateRetry)
                    .willReturn(
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

            val provider = getProvider()

            val resolvedContext = provider.resolveContext(Context(REVISION))
            resolvedContext.name shouldBe "0a4721665650ba7143871b22ef878e5b81c8f8b5"

            val serveEvents = server.allServeEvents.sortedBy { it.request.loggedDate }
            serveEvents shouldHaveSize 2
            serveEvents[1].request.loggedDate.toInstant().epochSecond shouldBeGreaterThanOrEqual retryTime
        }
    }
})

/** A mock for GitHub API to be used by tests. */
private val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

internal const val OWNER = "owner"
internal const val REPOSITORY = "repository"
internal const val REVISION = "configs-branch"
internal const val REVISION_HASH = "0a4721665650ba7143871b22ef878e5b81c8f8b5"
internal const val CONFIG_PATH = "config/app.config"
internal const val DIRECTORY_PATH = "config"
internal const val CONTENT = "repository: repo, username: user, password: pass"
internal const val NOT_FOUND = "NotFound"
internal const val API_TOKEN = "test-api-token"

/**
 * Return a [GitHubConfigFileProvider] instance with a default configuration and optional additional [properties].
 */
private fun getProvider(vararg properties: Pair<String, String>): GitHubConfigFileProvider {
    val secretProvider = mockk<ConfigSecretProvider>()

    every { secretProvider.getSecret(TOKEN) } returns API_TOKEN

    val config = ConfigFactory.parseMap(
        mapOf(
            GITHUB_API_URL to server.baseUrl(),
            REPOSITORY_OWNER to OWNER,
            REPOSITORY_NAME to REPOSITORY,
            DEFAULT_BRANCH to REVISION,
            LOCK_CHECK_INTERVAL_SEC to "1",
            CACHE_MAX_AGE_DAYS to "1",
            CACHE_CLEANUP_RATIO to "0"
        ) + properties
    )

    return GitHubConfigFileProvider.create(config, secretProvider)
}

/**
 * Prepare stubbing of a GET request with the expected authorization header.
 */
private fun authorizedGet(pattern: UrlPattern): MappingBuilder =
    get(pattern).withHeader("Authorization", equalTo("Bearer $API_TOKEN"))

/**
 * A stub for successfully getting the default branch.
 */
private fun WireMockServer.stubDefaultBranch() {
    stubFor(
        authorizedGet(
            urlEqualTo("/repos/$OWNER/$REPOSITORY")
        ).willReturn(
            okJson(
                """
                    {
                      "default_branch": "$REVISION"
                    }
                """.trimIndent()
            )
        )
    )
}

/**
 * A stub for successfully resolving a given [revision].
 */
private fun WireMockServer.stubExistingRevision(revision: String = REVISION) {
    stubFor(
        authorizedGet(
            urlEqualTo("/repos/$OWNER/$REPOSITORY/branches/$revision")
        ).willReturn(
            okJson(
                """
                    {
                      "name": "app.config",
                      "commit": {
                        "sha": "$REVISION_HASH",
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
        get(anyUrl()).willReturn(
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
                    """
                        {
                          "name": "app.config",
                          "path": "${CONFIG_PATH + 1}",
                          "sha": "0a4721665650ba7143871b22ef878e5b81c8f8b5",
                          "type": "file",
                          "size": 303
                        }
                    """.trimIndent()
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
                    """
                        {
                          "name": "app.config",
                          "path": "$CONFIG_PATH",
                          "sha": "0a4721665650ba7143871b22ef878e5b81c8f8b5",
                          "size": 303,
                          "type": "file",
                          "content": "${Base64.encode(CONTENT.toByteArray())}"
                        }
                    """.trimIndent()
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
                    """
                        [
                          {
                              "name": "app1.config",
                              "path": "${CONFIG_PATH + 1}",
                              "sha": "0a4721665650ba7143871b22ef878e5b81c8f8b6",
                              "type": "file",
                              "size": 908
                          },
                          {
                              "name": "app2.config",
                              "path": "${CONFIG_PATH + 2}",
                              "sha": "0a4721665650ba7143871b22ef878e5b81c8f8b7",
                              "type": "file",
                              "size": 305
                          },
                          {
                              "name": "other configs",
                              "path": "${DIRECTORY_PATH + 1}",
                              "sha": "0a4721665650ba7143871b22ef878e5b81c8f8b5",
                              "type": "dir",
                              "size": 303
                          }
                        ]
                    """.trimIndent()
                )
            )
    )
}
