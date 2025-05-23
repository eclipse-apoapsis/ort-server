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

package org.eclipse.apoapsis.ortserver.config.local

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

import java.io.File

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path

class LocalConfigFileProviderFactoryTest : WordSpec({
    "A correctly initialized provider instance" should {
        "be created" {
            val content = "content"
            val path = Path("config-file")
            val directory = tempdir()
            directory.resolve(path.path).writeText(content)

            val manager = createConfigManager(directory)
            manager.getFile(Context(""), path).bufferedReader(Charsets.UTF_8)
                .use { it.readText() } shouldBe content
        }
    }
})

/**
 * Create a [ConfigManager] object that uses a [LocalConfigFileProvider] to read config files from the provided
 * [directory].
 */
private fun createConfigManager(directory: File): ConfigManager =
    ConfigManager.create(createProviderConfig(directory))

/**
 * Create a [Config] that can be used to instantiate a [ConfigManager] which uses a [LocalConfigFileProvider] to
 * read config files from the provided [directory].
 */
private fun createProviderConfig(directory: File): Config {
    val providerMap = mapOf(
        ConfigManager.FILE_PROVIDER_NAME_PROPERTY to LocalConfigFileProviderFactory.NAME,
        ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
        LocalConfigFileProvider.CONFIG_DIR to directory.absolutePath
    )

    val configMap = mapOf(ConfigManager.CONFIG_MANAGER_SECTION to providerMap)

    return ConfigFactory.parseMap(configMap)
}
