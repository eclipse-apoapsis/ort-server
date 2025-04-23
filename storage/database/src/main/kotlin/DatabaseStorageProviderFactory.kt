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

package org.eclipse.apoapsis.ortserver.storage.database

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.storage.StorageProvider
import org.eclipse.apoapsis.ortserver.storage.StorageProviderFactory

/**
 * [StorageProviderFactory] implementation for the database storage implementation. This factory creates a
 * [StorageProvider] that uses a database table with a column referencing a PostgreSQL large object for storing
 * arbitrary data. Large objects are used since they do not require the whole data to be kept in memory. They
 * have, however, the limitation that data can only be accessed in a transaction. This is problematic here because
 * clients of the [StorageProvider] interface consume the returned stream later. To solve this, the implementation
 * actually reads data into memory if it is small enough. Otherwise, it copies the data into a temporary file which
 * is deleted after the client has consumed the stream.
 *
 * The underlying table can be shared between multiple storage providers. To distinguish between different providers,
 * a namespace has to be specified in the configuration. The configuration also needs to contain the threshold for the
 * data size that cannot be loaded into memory.
 *
 * This implementation uses the same database as ORT Server itself; so no dedicated database configuration is
 * required or supported.
 *
 * See https://jdbc.postgresql.org/documentation/binary-data/
 */
class DatabaseStorageProviderFactory : StorageProviderFactory {
    companion object {
        /** The name of this storage implementation. */
        const val NAME = "database"

        /**
         * The name of the configuration property to define the namespace under which the data is stored in the
         * database.
         */
        const val NAMESPACE_PROPERTY = "namespace"

        /**
         * The name of the configuration property that determines up to which size BLOBs can be loaded into memory.
         * When the data size is beyond this limit, data is buffered in temporary files.
         */
        const val MEMORY_LIMIT_PROPERTY = "inMemoryLimit"
    }

    override val name: String = NAME

    override fun createProvider(config: ConfigManager): StorageProvider =
        DatabaseStorageProvider(config.getString(NAMESPACE_PROPERTY), config.getInt(MEMORY_LIMIT_PROPERTY))
}
