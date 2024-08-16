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
import java.io.IOException
import java.io.InputStream

import org.eclipse.apoapsis.ortserver.config.ConfigManager

/**
 * A simple implementation of the [StorageProviderFactory] interface for testing purposes that stores data in memory.
 */
class StorageProviderFactoryForTesting : StorageProviderFactory {
    companion object {
        /** The name of this provider implementation. */
        const val NAME = "storageProviderFactoryForTesting"

        /**
         * Name of a configuration property that defines the error key. If set, all keys containing this string cause
         * the test storage to throw an exception.
         */
        const val ERROR_KEY_PROPERTY = "errorKey"

        /**
         * Stores the latest provider instance that has been created. This can be used to access this instance from
         * outside and to test the content of its storage.
         */
        private var latestInstance: StorageProvider? = null

        /**
         * Stores the map with data of the latest provider instance that has been created. This can be used to query
         * or set test data directly.
         */
        @Suppress("DoubleMutabilityForCollection")
        private var latestData: MutableMap<Key, Entry> = mutableMapOf()

        /**
         * Return the [Entry] from the latest [StorageProvider] instance created by this factory that is associated
         * with the given [key].
         */
        fun getEntry(key: Key): Entry = latestData.getValue(key)

        /**
         * Store the given [entry] under the given [key] in the data store of the latest [StorageProvider] created by
         * this factory.
         */
        fun putEntry(key: Key, entry: Entry) {
            latestData[key] = entry
        }

        /**
         * Return a set with the keys that are currently in the data of the latest [StorageProvider] created by this
         * factory.
         */
        fun keys(): Set<Key> = latestData.keys

        /**
         * An internal class used to manage the data stored in this test storage.
         */
        class Entry(
            val data: ByteArray,
            val contentType: String?,
            val length: Long
        )
    }

    override val name: String = NAME

    override fun createProvider(config: ConfigManager): StorageProvider {
        val storage = mutableMapOf<Key, Entry>()
        val errorKey = if (config.hasPath(ERROR_KEY_PROPERTY)) config.getString(ERROR_KEY_PROPERTY) else "<undefined>"

        return object : StorageProvider {
            override fun read(key: Key): StorageEntry =
                getEntry(key)?.let { entry ->
                    StorageEntry.create(
                        data = ByteArrayInputStream(entry.data),
                        contentType = entry.contentType,
                        length = entry.length
                    )
                } ?: throw IOException("Could not resolve key '${key.key}'.")

            override fun write(key: Key, data: InputStream, length: Long, contentType: String?) {
                getEntry(key)

                data.use {
                    storage[key] = Entry(it.readAllBytes(), contentType, length)
                }
            }

            override fun contains(key: Key): Boolean = getEntry(key) != null

            override fun delete(key: Key): Boolean {
                val entry = getEntry(key)

                storage -= key

                return entry != null
            }

            /**
             * Check whether [key] should trigger an exception. Otherwise, return the associated [Entry].
             */
            private fun getEntry(key: Key): Entry? {
                if (errorKey in key.key) throw IOException("Test exception from StorageProviderFactoryForTesting.")

                return storage[key]
            }
        }.also {
            latestInstance = it
            latestData = storage
        }
    }
}
