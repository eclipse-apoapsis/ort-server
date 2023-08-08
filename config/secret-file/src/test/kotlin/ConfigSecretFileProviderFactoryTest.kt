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

package org.ossreviewtoolkit.server.config.secret.file

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.server.config.ConfigFileProviderFactoryForTesting
import org.ossreviewtoolkit.server.config.ConfigManager
import org.ossreviewtoolkit.server.config.Path

class ConfigSecretFileProviderFactoryTest : StringSpec({
    fun createSecretFile(key: String, value: String): File =
        tempfile().also { file ->
            file.writeText("$key=$value")
        }

    "A correctly initialized provider instance should be created" {
        val path = "testSecret"
        val value = "testSecretValue"
        val manager = createConfigManager(
            createSecretFile("s1", "v1"),
            createSecretFile("s2", "v2"),
            createSecretFile(path, value),
            createSecretFile(path, "another value")
        )

        manager.getSecret(Path(path)) shouldBe value
    }
})

/**
 * Create a [ConfigManager] object that uses a [ConfigSecretFileProvider] which reads secrets from the provided
 * [secretFiles].
 */
private fun createConfigManager(vararg secretFiles: File): ConfigManager =
    ConfigManager.create(createProviderConfig(*secretFiles), ConfigManager.DEFAULT_CONTEXT)

/**
 * Create a [Config] that can be used to instantiate a [ConfigManager] which uses a [ConfigSecretFileProvider] to
 * query secrets. The secrets are read from the given [secretFiles].
 */
private fun createProviderConfig(vararg secretFiles: File): Config {
    val providerMap = mapOf(
        ConfigManager.FILE_PROVIDER_NAME_PROPERTY to ConfigFileProviderFactoryForTesting.NAME,
        ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretFileProviderFactory.NAME,
        "configSecretFileList" to secretFiles.joinToString { it.absolutePath }
    )
    val configMap = mapOf(ConfigManager.CONFIG_MANAGER_SECTION to providerMap)

    return ConfigFactory.parseMap(configMap)
}
