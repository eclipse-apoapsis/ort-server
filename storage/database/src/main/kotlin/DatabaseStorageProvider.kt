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

import java.io.InputStream

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.StorageEntry
import org.eclipse.apoapsis.ortserver.storage.StorageProvider

import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Implementation of the [StorageProvider] interface that is backed by a database table using PostgreSQL's large
 * objects.
 */
class DatabaseStorageProvider(
    /** The namespace under which data is to be stored. */
    private val namespace: String,

    /** The maximum size of a storage entry that can be loaded into memory. */
    private val inMemoryLimit: Int
) : StorageProvider {
    override fun read(key: Key): StorageEntry = transaction {
        val entry = findByKey(key).single()

        val inputStream = readLargeObject(entry.data, entry.size, inMemoryLimit)
        StorageEntry.create(inputStream, entry.contentType, entry.size)
    }

    override fun write(key: Key, data: InputStream, length: Long, contentType: String?) {
        transaction {
            // In case of an override, delete the key first. This may not be the cleanest solution (it has the
            // side effect that the createdAt date is changed), but it is very easy to implement.
            deleteKey(key)

            StorageDao.new {
                createdAt = Clock.System.now()
                namespace = this@DatabaseStorageProvider.namespace
                size = length
                this.key = key.key
                this.contentType = contentType
                this.data = storeLargeObject(data)
            }
        }
    }

    override fun contains(key: Key): Boolean = transaction {
        !findByKey(key).empty()
    }

    override fun delete(key: Key): Boolean = transaction {
        deleteKey(key)
    }

    /**
     * Delete the entry with the given [key] from this storage. The caller is responsible for transaction management.
     */
    private fun Transaction.deleteKey(key: Key) =
        findByKey(key).singleOrNull()?.let { dao ->
            deleteLargeObject(dao.data)
            dao.delete()
            true
        } ?: false

    /**
     * Search for an entry with the given [key].
     */
    private fun findByKey(key: Key): SizedIterable<StorageDao> =
        StorageDao.find { (StorageTable.key eq key.key) and (StorageTable.namespace eq namespace) }
}
