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

package org.ossreviewtoolkit.server.secrets

import org.ossreviewtoolkit.server.config.ConfigManager

/**
 * A factory interface for creating [SecretsProvider] instances.
 *
 * Each concrete implementation of a secrets storage must provide such a factory class. The factories available are
 * looked up via the service loader mechanism. They are then used to create a [SecretsProvider] based on the
 * current configuration.
 */
interface SecretsProviderFactory {
    /**
     * The name of this implementation. This is used to match the factory selected in the application configuration.
     */
    val name: String

    /**
     * Create the [SecretsProvider] managed by this factory. Use [configManager] to obtain the required,
     * implementation-specific configuration settings.
     */
    fun createProvider(configManager: ConfigManager): SecretsProvider
}
