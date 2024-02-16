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

package org.eclipse.apoapsis.ortserver.storage

import org.eclipse.apoapsis.ortserver.config.ConfigManager

/**
 * Definition of a factory interface for creating a concrete [StorageProvider] instance.
 *
 * The storage implementation to be used for a specific use case can be defined in the application configuration.
 * Based on this, a factory (matched by its [name] property) is looked up from a service loader and asked to create the
 * corresponding provider object.
 */
interface StorageProviderFactory {
    /** Defines a name for this concrete storage implementation. */
    val name: String

    /**
     * Return an initialized [StorageProvider] using the given [config].
     */
    fun createProvider(config: ConfigManager): StorageProvider
}
