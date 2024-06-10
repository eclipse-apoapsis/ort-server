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

package org.eclipse.apoapsis.ortserver.secrets.scaleway

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.secrets.scaleway.ScalewayConfiguration.Companion.DEFAULT_REGION

class ScalewayConfigurationTest : StringSpec({
    "Configuration can be created from enum names" {
        val scalewayProperties = mapOf(NAME_OF_API_VERSION to "V1_BETA1", NAME_OF_PROJECT_ID to "projectId")
        val config = ScalewayConfiguration.create(createConfigManager(scalewayProperties))

        config.apiVersion shouldBe ScalewayApiVersion.V1_BETA1
        config.region shouldBe DEFAULT_REGION
    }
})

/**
 * Return a [ConfigManager] that wraps the given [scalewayProperties]. In addition, access to the secrets is possible.
 */
private fun createConfigManager(scalewayProperties: Map<String, Any>): ConfigManager {
    val secretProperties = mapOf(NAME_OF_SECRET_KEY to "secretKey")

    val configManagerProperties = mapOf(
        ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
        ConfigSecretProviderFactoryForTesting.SECRETS_PROPERTY to secretProperties
    )

    val properties = scalewayProperties + mapOf(ConfigManager.CONFIG_MANAGER_SECTION to configManagerProperties)

    return ConfigManager.create(ConfigFactory.parseMap(properties))
}
