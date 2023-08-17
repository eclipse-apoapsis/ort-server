/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.config.github

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll

import java.io.ByteArrayInputStream

import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.ConfigSecretProviderFactoryForTesting
import org.ossreviewtoolkit.server.config.Context
import org.ossreviewtoolkit.server.config.Path

class GitHubConfigFileProviderFactoryTest : WordSpec() {
    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        unmockkAll()
    }

    init {
        "A correctly initialized provider instance" should {
            "be created" {
                mockkConstructor(GitHubConfigFileProvider::class)
                every { anyConstructed<GitHubConfigFileProvider>().getFile(any(), any()) } returns ByteArrayInputStream(
                    CONTENT.toByteArray()
                )

                val manager = createConfigManager()
                manager.getFile(Context(REVISION), Path(CONFIG_PATH)).bufferedReader(Charsets.UTF_8)
                    .use { it.readText() } shouldBe CONTENT
            }
        }
    }

    /**
     * Create a [ConfigManager] object that uses a [GitHubConfigFileProvider] which reads secrets from the provided
     * [server].
     */
    private fun createConfigManager(): ConfigManager = ConfigManager.create(createProviderConfig())

    /**
     * Create a [Config] that can be used to instantiate a [ConfigManager] which uses a [GitHubConfigFileProvider] to
     * query config files. The files are read from the given [server].
     */
    private fun createProviderConfig(): Config {
        val providerMap = mapOf(
            ConfigManager.FILE_PROVIDER_NAME_PROPERTY to GitHubConfigFileProviderFactory.NAME,
            ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
            GitHubConfigFileProvider.GITHUB_API_URL to "http://localhost:8080",
            GitHubConfigFileProvider.REPOSITORY_OWNER to OWNER,
            GitHubConfigFileProvider.REPOSITORY_NAME to REPOSITORY
        )
        val configMap = mapOf(ConfigManager.CONFIG_MANAGER_SECTION to providerMap)

        return ConfigFactory.parseMap(configMap)
    }
}
