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

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe

import java.sql.Connection
import java.sql.SQLException

import kotlin.math.abs
import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.storage.Storage.Companion.dataArray
import org.eclipse.apoapsis.ortserver.storage.Storage.Companion.dataString
import org.eclipse.apoapsis.ortserver.storage.StorageException

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

import org.postgresql.PGConnection

class DatabaseStorageTest : WordSpec({
    extension(DatabaseTestExtension())

    "write" should {
        "add an entry into the database" {
            val key = Key("newEntry")
            val data = "This is the data to be added."
            val contentType = "text/plain"

            val storage = createStorage()
            storage.write(key, data, contentType)

            transaction {
                val result = StorageTable.selectAll().where {
                    (StorageTable.key eq key.key) and (StorageTable.namespace eq NAMESPACE)
                }.first()

                val deltaT = abs(Clock.System.now().minus(result[StorageTable.createdAt]).inWholeMilliseconds)
                deltaT shouldBeLessThan 3000
                result[StorageTable.size] shouldBe data.length
            }
        }

        "override an entry in the database" {
            val key = Key("newAndUpdatedEntry")
            val data = "This is the updated data of the test entry."
            val contentType = "test/content"

            val storage = createStorage()
            storage.write(key, "originalData", "text/original")

            storage.write(key, data, contentType)

            storage.read(key).use { entry ->
                entry.dataString shouldBe data
                entry.contentType shouldBe contentType
            }
        }
    }

    "read" should {
        "read an existing entry from the database" {
            val key = Key("existingEntry")
            val data = "The data from the storage."
            val contentType = "application/octet-stream"

            val storage = createStorage()
            storage.write(key, data, contentType)

            storage.read(key).use { entry ->
                entry.dataString shouldBe data
                entry.contentType shouldBe contentType
            }
        }

        "read a data entry that does not fit into memory" {
            val key = Key("largeEntry")
            val data = ByteArray(256) { it.toByte() }

            val storage = createStorage()
            storage.write(key, data)

            storage.read(key).use { entry ->
                entry.dataArray shouldBe data
            }
        }

        "throw an exception if the key cannot be resolved" {
            val storage = createStorage()

            shouldThrow<StorageException> {
                storage.read(Key("This_cannot_be_found"))
            }
        }
    }

    "contains" should {
        "return false for a non existing entry" {
            val storage = createStorage()

            storage.containsKey(Key("nonExistingKey")) shouldBe false
        }

        "return true for an existing entry" {
            val key = Key("theKey")

            val storage = createStorage()
            storage.write(key, "someData")

            storage.containsKey(key) shouldBe true
        }

        "return false for an existing entry in a different namespace" {
            val key = Key("namespaceEntry")
            val data = "The data from the storage in another namespace."

            val otherStorage = createStorage(namespace = "otherNamespace")
            otherStorage.write(key, data)

            val storage = createStorage()
            storage.containsKey(key) shouldBe false
        }
    }

    "delete" should {
        "delete an entry from the database" {
            val key = Key("toBeDeleted")

            val storage = createStorage()
            storage.write(key, "Data to be deleted...")

            storage.delete(key) shouldBe true
            storage.containsKey(key) shouldBe false
        }

        "return false for a non-existing entry" {
            val key = Key("nonExisting")

            val storage = createStorage()

            storage.delete(key) shouldBe false
        }

        "delete the large object associated with the key" {
            val key = Key("toBeFullyDeleted")

            val storage = createStorage()
            storage.write(key, "This is data to be stored in a large object and will then be deleted.")

            val oid = transaction {
                val result = StorageTable.selectAll().where {
                    (StorageTable.key eq key.key) and (StorageTable.namespace eq NAMESPACE)
                }.first()
                result[StorageTable.data]
            }

            storage.delete(key) shouldBe true

            transaction {
                val manager = (connection.connection as Connection).unwrap(PGConnection::class.java).largeObjectAPI

                shouldThrow<SQLException> {
                    manager.open(oid)
                }
            }
        }
    }
})

/** The namespace used by the storage tests. */
private const val NAMESPACE = "test-storage"

/**
 * Create a [Storage] that is configured to use the [DatabaseStorageProvider] implementation for the given [namespace].
 */
private fun createStorage(namespace: String = NAMESPACE): Storage {
    val config = ConfigFactory.parseMap(
        mapOf(
            namespace to mapOf("name" to "database", "namespace" to namespace, "inMemoryLimit" to 32)
        )
    )

    return Storage.create(namespace, ConfigManager.create(config))
}
