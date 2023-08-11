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

package org.ossreviewtoolkit.server.config

import com.typesafe.config.Config

/**
 * A factory interface for creating [ConfigFileProvider] instances.
 */
interface ConfigFileProviderFactory {
    /**
     * The name of the [ConfigFileProvider] implementation. This is used to load a specific factory from the classpath.
     */
    val name: String

    /**
     * Create a new [ConfigFileProvider] instance based on the given [config]. If required, secrets can be obtained
     * from the given [secretProvider]. The object returned by this function should be fully initialized, so that it
     * can be used to load configuration files.
     */
    fun createProvider(config: Config, secretProvider: ConfigSecretProvider): ConfigFileProvider
}
