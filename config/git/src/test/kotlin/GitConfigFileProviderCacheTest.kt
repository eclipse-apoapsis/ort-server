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

package org.eclipse.apoapsis.ortserver.config.git

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.spyk
import io.mockk.verify

import java.nio.file.Files

import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.git.GitConfigFileProvider.Companion.MAX_REVISION_CACHE_SIZE

private const val TEST_GIT_URL = "https://example.org/config-repo.git"

/**
 * Tests for the revision caching of [GitConfigFileProvider]. These tests stub the (expensive) revision resolution, so
 * they do not require network access.
 */
class GitConfigFileProviderCacheTest : WordSpec({
    "create" should {
        "use the default cache TTL if the property is not set" {
            val config = ConfigFactory.parseMap(mapOf(GitConfigFileProvider.GIT_URL to TEST_GIT_URL))

            val provider = GitConfigFileProvider.create(config)

            provider.revisionCacheTtl shouldBe GitConfigFileProvider.DEFAULT_REVISION_CACHE_TTL_SECONDS
        }

        "use the configured cache TTL" {
            val config = ConfigFactory.parseMap(
                mapOf(
                    GitConfigFileProvider.GIT_URL to TEST_GIT_URL,
                    GitConfigFileProvider.GIT_REVISION_CACHE_TTL_SECONDS to 120
                )
            )

            val provider = GitConfigFileProvider.create(config)

            provider.revisionCacheTtl shouldBe 120.seconds
        }
    }

    "resolveContext" should {
        "cache the resolved revision within the TTL" {
            val timeSource = TestTimeSource()
            val provider = createProviderSpy(timeSource)
            every { provider.resolveRevision("main") } returns "sha-main"

            provider.resolveContext(Context("main")).name shouldBe "sha-main"

            timeSource += 59.seconds
            provider.resolveContext(Context("main")).name shouldBe "sha-main"

            verify(exactly = 1) { provider.resolveRevision("main") }
        }

        "resolve the revision again after the TTL has elapsed" {
            val timeSource = TestTimeSource()
            val provider = createProviderSpy(timeSource)
            every { provider.resolveRevision("main") } returnsMany listOf("sha-old", "sha-new")

            provider.resolveContext(Context("main")).name shouldBe "sha-old"

            timeSource += 61.seconds
            provider.resolveContext(Context("main")).name shouldBe "sha-new"

            verify(exactly = 2) { provider.resolveRevision("main") }
        }

        "cache distinct contexts independently" {
            val timeSource = TestTimeSource()
            val provider = createProviderSpy(timeSource)
            every { provider.resolveRevision("main") } returns "sha-main"
            every { provider.resolveRevision("dev") } returns "sha-dev"

            provider.resolveContext(Context("main")).name shouldBe "sha-main"
            provider.resolveContext(Context("dev")).name shouldBe "sha-dev"
            provider.resolveContext(Context("main")).name shouldBe "sha-main"

            verify(exactly = 1) { provider.resolveRevision("main") }
            verify(exactly = 1) { provider.resolveRevision("dev") }
        }

        "evict the least recently used entry when the cache is full" {
            val timeSource = TestTimeSource()
            val provider = createProviderSpy(timeSource)
            every { provider.resolveRevision(any()) } answers { "sha-${firstArg<String>()}" }

            // Fill the cache up to its maximum capacity. The first inserted entry is the least recently used.
            repeat(MAX_REVISION_CACHE_SIZE) { provider.resolveContext(Context("branch-$it")) }

            // Insert one more entry, which must evict the least recently used entry ("branch-0").
            provider.resolveContext(Context("branch-$MAX_REVISION_CACHE_SIZE"))

            // The evicted entry must be resolved anew, while a still-cached entry must not.
            provider.resolveContext(Context("branch-0"))
            provider.resolveContext(Context("branch-${MAX_REVISION_CACHE_SIZE - 1}"))

            verify(exactly = 2) { provider.resolveRevision("branch-0") }
            verify(exactly = 1) { provider.resolveRevision("branch-${MAX_REVISION_CACHE_SIZE - 1}") }
        }
    }
})

private fun createProviderSpy(timeSource: TestTimeSource) =
    spyk(
        GitConfigFileProvider(
            TEST_GIT_URL,
            Files.createTempDirectory("git-config-cache-test").toFile(),
            revisionCacheTtl = 60.seconds,
            timeSource = timeSource
        )
    )
