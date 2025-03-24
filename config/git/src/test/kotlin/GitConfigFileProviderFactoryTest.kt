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

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkAll

import java.io.ByteArrayInputStream

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path

private const val CONTENT = "Hello world"

class GitConfigFileProviderFactoryTest : WordSpec({
    afterEach {
        unmockkAll()
    }

    "A correctly initialized provider instance" should {
        "be created" {
            mockkConstructor(GitConfigFileProvider::class)
            every { anyConstructed<GitConfigFileProvider>().getFile(any(), any()) } returns ByteArrayInputStream(
                CONTENT.toByteArray()
            )

            val manager = ConfigManager.create(createProviderConfig())
            manager.getFile(Context(GIT_BRANCH_MAIN), Path("README.md")).bufferedReader(Charsets.UTF_8)
                .use { it.readText() } shouldBe CONTENT
        }
    }
})

/**
 * Create a [Config] that can be used to instantiate a [ConfigManager] which uses a [GitConfigFileProvider] to
 * read config files.
 */
private fun createProviderConfig(): Config {
    val providerMap = mapOf(
        ConfigManager.FILE_PROVIDER_NAME_PROPERTY to GitConfigFileProviderFactory.NAME,
        ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
        GitConfigFileProvider.GIT_URL to GIT_URL
    )

    val configMap = mapOf(ConfigManager.CONFIG_MANAGER_SECTION to providerMap)

    return ConfigFactory.parseMap(configMap)
}
