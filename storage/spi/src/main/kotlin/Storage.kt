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

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.ServiceLoader

import kotlinx.coroutines.supervisorScope

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

/**
 * A class allowing convenient access to a concrete [StorageProvider] implementation.
 *
 * Instead of directly interacting with a [StorageProvider], an instance of this class can be used for this purpose.
 * The [create] factory function looks up the correct provider based on the provided parameters and obtains a
 * properly initialized instance. Via this instance, the storage can be queried and updated using a richer API.
 * Also, proprietary exceptions thrown by the underlying [StorageProvider] implementation are caught and mapped to
 * [StorageException] exceptions.
 */
class Storage(
    /** The wrapped [StorageProvider]. */
    private val provider: StorageProvider
) {
    companion object {
        /** The name of the configuration property for selecting a [StorageProviderFactory]. */
        private const val FACTORY_NAME_PROPERTY = "name"

        /** The service loader for loading factory implementations from the classpath. */
        private val LOADER = ServiceLoader.load(StorageProviderFactory::class.java)

        /**
         * Return a [Storage] instance that can be used to interact with a concrete storage implementation. Obtain
         * the underlying [StorageProvider] by looking up the factory that has been configured in the given [config]
         * for the given [storageType].
         */
        fun create(storageType: String, config: ConfigManager): Storage {
            val storageConfig = runCatching {
                config.subConfig(Path(storageType))
            }.getOrElse { e ->
                throw StorageException("No storage configuration found for storage type '$storageType'.", e)
            }

            val factoryName = storageConfig.getStringOrNull(FACTORY_NAME_PROPERTY)
                ?: throw StorageException("Missing '$FACTORY_NAME_PROPERTY' property in the '$storageType' section.")

            val factory = LOADER.find { it.name == factoryName }
                ?: throw StorageException("StorageProviderFactory '$factoryName' not found on classpath.")

            return Storage(factory.createProvider(storageConfig))
        }

        /**
         * Return the data stored in this [StorageEntry] as an array of bytes. Note: This reads all data into memory.
         */
        val StorageEntry.dataArray: ByteArray
            get() = data.readAllBytes()

        /**
         * Return the data stored in this [StorageEntry] as a string. Note: This reads all data into memory.
         */
        val StorageEntry.dataString: String
            get() = String(dataArray)
    }

    /**
     * Return the [StorageEntry] associated with the given [key]. Throw a [StorageEntry] if this operation fails.
     */
    suspend fun read(key: Key): StorageEntry = wrapException {
        read(key)
    }

    /**
     * Write the given [data] with the given [length] and optional [contentType] into this storage and associate it
     * with the given [key].  Throw a [StorageException] if this operation fails. Note that the caller is responsible
     * for closing the provided [InputStream].
     */
    suspend fun write(key: Key, data: InputStream, length: Long, contentType: String? = null) {
        wrapException {
            write(key, data, length, contentType)
        }
    }

    /**
     * Write the given [array][data] into this storage and associate it with the given [key]. Set the optional
     * [contentType]. Throw a [StorageException] if this operation fails.
     */
    suspend fun write(key: Key, data: ByteArray, contentType: String? = null) {
        ByteArrayInputStream(data).use { stream ->
            write(key, stream, data.size.toLong(), contentType)
        }
    }

    /**
     * Write the given [string][data] into this storage and associate it with the given [key]. Set the optional
     * [contentType]. Throw a [StorageException] if this operation fails.
     */
    suspend fun write(key: Key, data: String, contentType: String? = null) {
        write(key, data.toByteArray(), contentType)
    }

    /**
     * Return a flag whether the given [key] is contained in this storage. Throw a [StorageException] if this
     * operation fails.
     */
    suspend fun containsKey(key: Key): Boolean = wrapException { contains(key) }

    /**
     * Delete the given [key] from this storage and return a flag whether it existed before. Throw a
     * [StorageException] if this operation fails.
     */
    suspend fun delete(key: Key): Boolean = wrapException { provider.delete(key) }

    /**
     * Execute [block] on the wrapped [StorageProvider] and map occurring exceptions to [StorageException]s.
     */
    private suspend fun <T> wrapException(block: suspend StorageProvider.() -> T): T =
        @Suppress("TooGenericExceptionCaught")
        try {
            // To prevent the StorageException being suppressed by the coroutine exception handler, a new scope is
            // created here.
            supervisorScope {
                provider.block()
            }
        } catch (e: Exception) {
            throw StorageException("Exception from StorageProvider.", e)
        }
}

/**
 * An exception class for reporting all error conditions related to dealing with a [Storage] instance.
 *
 * The [Storage] class wraps all exceptions thrown by the wrapped [StorageProvider] into exceptions of this type.
 */
class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
