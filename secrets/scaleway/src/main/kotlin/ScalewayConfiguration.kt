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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.utils.config.getEnumOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrDefault

internal const val NAME_OF_SERVER_URL = "scwServerUrl"
internal const val NAME_OF_API_VERSION = "scwApiVersion"
internal const val NAME_OF_REGION = "scwRegion"
internal const val NAME_OF_SECRET_KEY = "scwSecretKey"
internal const val NAME_OF_PROJECT_ID = "scwProjectId"

data class ScalewayConfiguration(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val apiVersion: ScalewayApiVersion = DEFAULT_API_VERSION,
    val region: ScalewayRegion = DEFAULT_REGION,
    val secretKey: String,
    val projectId: String
) {
    companion object {
        const val DEFAULT_SERVER_URL = "https://api.scaleway.com/"

        val DEFAULT_API_VERSION = ScalewayApiVersion.V1_BETA1
        val DEFAULT_REGION = ScalewayRegion.FRANCE_PARIS

        fun create(configManager: ConfigManager) =
            ScalewayConfiguration(
                serverUrl = configManager.getStringOrDefault(NAME_OF_SERVER_URL, DEFAULT_SERVER_URL),
                apiVersion = configManager.getEnumOrDefault(NAME_OF_API_VERSION, DEFAULT_API_VERSION),
                region = configManager.getEnumOrDefault(NAME_OF_REGION, DEFAULT_REGION),
                secretKey = configManager.getSecret(Path(NAME_OF_SECRET_KEY)),
                projectId = configManager.getString(NAME_OF_PROJECT_ID)
            )
    }

    val hasCredentials = secretKey.isNotEmpty() && projectId.isNotEmpty()
}

/**
 * See https://www.scaleway.com/en/developers/api/#versions.
 */
@Serializable
enum class ScalewayApiVersion {
    @SerialName("v1alpha1") V1_ALPHA1,
    @SerialName("v1beta1") V1_BETA1;

    // Align the string representation with the serial name to make enum usage in URL patterns work.
    override fun toString() = serializer().descriptor.getElementName(ordinal)
}

/**
 * See https://www.scaleway.com/en/developers/api/#regions-and-zones.
 */
@Serializable
enum class ScalewayRegion {
    @SerialName("fr-par") FRANCE_PARIS,
    @SerialName("nl-ams") NETHERLANDS_AMSTERDAM,
    @SerialName("pl-waw") POLAND_WARSAW;

    // Align the string representation with the serial name to make enum usage in URL patterns work.
    override fun toString() = serializer().descriptor.getElementName(ordinal)
}
