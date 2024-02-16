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

package org.ossreviewtoolkit.server.config

import com.typesafe.config.Config

/**
 * A factory interface for creating [ConfigSecretProvider] instances.
 */
interface ConfigSecretProviderFactory {
    /**
     * The name of the [ConfigSecretProvider] implementation. This is used to load a specific factory from the
     * classpath.
     */
    val name: String

    /**
     * Create a new [ConfigSecretProvider] instance based on the given [config]. The object returned by this function
     * should be fully initialized, so that it can be used to query the values of secrets.
     */
    fun createProvider(config: Config): ConfigSecretProvider
}
