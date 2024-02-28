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

package org.eclipse.apoapsis.ortserver.api.v1.model

import kotlinx.serialization.Serializable

/**
 * The configuration of provider plugins.
 */
@Serializable
data class ProviderPluginConfiguration(
    /**
     * The type of the provider. Must match the type of an available ORT plugin.
     */
    val type: String,

    /**
     * A unique identifier for the provider.
     */
    val id: String = type,

    /**
     * Whether this provider is enabled.
     */
    val enabled: Boolean = true,

    /**
     * The configuration options of the provider. See the specific implementation for available configuration options.
     */
    val options: Options = emptyMap(),

    /**
     * The configuration secrets of the provider. See the specific implementation for available secret options. Note
     * that values in this map are not the actual values of the secrets, but references to secrets in the secret
     * storage.
     */
    val secrets: Options = emptyMap()
)
