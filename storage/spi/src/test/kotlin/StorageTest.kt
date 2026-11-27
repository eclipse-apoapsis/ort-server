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

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

import java.io.ByteArrayInputStream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.storage.Storage.Companion.dataArray
import org.eclipse.apoapsis.ortserver.storage.Storage.Companion.dataString

class StorageTest : WordSpec({
    "create" should {
        "throw an exception if no configuration for the given storage type is present" {
            val config = ConfigFactory.empty()

            shouldThrow<StorageException> {
                Storage.create(STORAGE_TYPE, ConfigManager.create(config), mockk())
            }
        }

        "throw an exception if no storage provider name has been configured" {
            val config = ConfigFactory.parseMap(
                mapOf(STORAGE_TYPE to mapOf("foo" to "bar"))
            )

            shouldThrow<StorageException> {
                Storage.create(STORAGE_TYPE, ConfigManager.create(config), mockk())
            }
        }

        "throw an exception if the configured provider is not found" {
            val providerName = "nonExistingProvider"
            val config = ConfigFactory.parseMap(
                mapOf(STORAGE_TYPE to mapOf("name" to providerName))
            )

            val exception = shouldThrow<StorageException> {
                Storage.create(STORAGE_TYPE, ConfigManager.create(config), mockk())
            }

            exception.localizedMessage shouldContain providerName
        }
    }

    "write" should {
        "write a stream with data into the storage" {
            val data = "This is test data".repeat(32).toByteArray()
            val key = Key("testData")

            val storage = createStorage()
            ByteArrayInputStream(data).use { stream ->
                storage.write(key, stream, data.size.toLong(), CONTENT_TYPE)
            }

            val entry = StorageProviderFactoryForTesting.getEntry(key)
            entry.contentType shouldBe CONTENT_TYPE
            entry.data shouldBe data
            entry.length shouldBe data.size
        }

        "handle exceptions thrown by the provider" {
            val storage = createStorage()

            shouldThrow<StorageException> {
                ByteArrayInputStream(ByteArray(1)).use { stream ->
                    storage.write(Key(ERROR_KEY), stream, 1L)
                }
            }
        }

        "write a byte array into the storage" {
            val data = "This is again test data".repeat(8).toByteArray()
            val key = Key("testDataArray")

            val storage = createStorage()
            storage.write(key, data, CONTENT_TYPE)

            val entry = StorageProviderFactoryForTesting.getEntry(key)
            entry.contentType shouldBe CONTENT_TYPE
            entry.data shouldBe data
            entry.length shouldBe data.size
        }

        "write a string into the storage" {
            val data = "A string with even more test data"
            val key = Key("testDataString")

            val storage = createStorage()
            storage.write(key, data, CONTENT_TYPE)

            val entry = StorageProviderFactoryForTesting.getEntry(key)
            entry.contentType shouldBe CONTENT_TYPE
            entry.data shouldBe data.toByteArray()
            entry.length shouldBe data.toByteArray().size
        }
    }

    "read" should {
        "return data as a stream" {
            val data = "Data to be read from the storage".repeat(8).toByteArray()
            val key = Key("testData")

            val storage = createStorage()
            StorageProviderFactoryForTesting.putEntry(
                key,
                StorageProviderFactoryForTesting.Companion.Entry(data, CONTENT_TYPE, data.size.toLong())
            )

            val entry = storage.read(key)

            entry.contentType shouldBe CONTENT_TYPE
            entry.data.use { it.readAllBytes() } shouldBe data
            entry.length shouldBe data.size.toLong()
        }

        "handle exceptions thrown by the provider" {
            val storage = createStorage()

            shouldThrow<StorageException> {
                storage.read(Key(ERROR_KEY))
            }
        }
    }

    "dataArray" should {
        "return data as an array" {
            val data = "Data to be read from the entry".repeat(8).toByteArray()

            StorageEntry.create(ByteArrayInputStream(data), CONTENT_TYPE, data.size.toLong()).use { entry ->
                entry.dataArray shouldBe data
            }
        }
    }

    "dataString" should {
        "return data as a String" {
            val data = "This is the data as string."

            StorageEntry.create(
                ByteArrayInputStream(data.toByteArray()), CONTENT_TYPE, data.length.toLong()
            ).use { entry ->
                entry.dataString shouldBe data
            }
        }
    }

    "containsKey" should {
        "return false for a non-existing key" {
            val storage = createStorage()

            storage.contains(Key("anyKey")) shouldBe false
        }

        "handle exceptions thrown by the provider" {
            val storage = createStorage()

            shouldThrow<StorageException> {
                storage.contains(Key(ERROR_KEY))
            }
        }

        "not wrap a CancellationException thrown by the provider" {
            val provider = mockk<StorageProvider> {
                coEvery { contains(any()) } throws CancellationException("Test cancellation.")
            }

            shouldThrow<CancellationException> {
                Storage(provider).contains(Key("anyKey"))
            }
        }
    }

    "delete" should {
        "return false for a non-existing key" {
            val storage = createStorage()

            storage.delete(Key("nonExistingKey")) shouldBe false
        }

        "delete an existing key" {
            val key = Key("keyToRemove")
            val entry = StorageProviderFactoryForTesting.Companion.Entry(
                data = "test".toByteArray(),
                contentType = "test",
                length = 42L
            )
            val otherKey = Key("notToRemove")
            val otherEntry = StorageProviderFactoryForTesting.Companion.Entry(
                data = "remains".toByteArray(),
                contentType = "persistent",
                length = 43L
            )

            val storage = createStorage()
            StorageProviderFactoryForTesting.putEntry(key, entry)
            StorageProviderFactoryForTesting.putEntry(otherKey, otherEntry)

            storage.delete(key) shouldBe true

            val keys = StorageProviderFactoryForTesting.keys()
            keys should containExactly(otherKey)
        }

        "handle exceptions thrown by the provider" {
            val storage = createStorage()

            shouldThrow<StorageException> {
                storage.delete(Key(ERROR_KEY))
            }
        }
    }

    "list" should {
        "return information about the entries with the given prefix" {
            val storage = createStorage()
            storage.write(Key("dir|entry1"), "data1", CONTENT_TYPE)
            storage.write(Key("dir|entry2"), "more data", CONTENT_TYPE)
            storage.write(Key("other|entry3"), "other data", CONTENT_TYPE)

            storage.list("dir|").toList() should containExactlyInAnyOrder(
                StorageEntryInfo(Key("dir|entry1"), 5L, CONTENT_TYPE, null),
                StorageEntryInfo(Key("dir|entry2"), 9L, CONTENT_TYPE, null)
            )
        }

        "return all entries if no prefix is provided" {
            val storage = createStorage()
            storage.write(Key("entry1"), "data1")
            storage.write(Key("entry2"), "data2")

            storage.list().toList().map { it.key } should containExactlyInAnyOrder(Key("entry1"), Key("entry2"))
        }

        "return an empty flow if there are no matching entries" {
            val storage = createStorage()
            storage.write(Key("entry"), "data")

            storage.list("nonExisting").toList() should beEmpty()
        }

        "handle exceptions thrown by the provider" {
            val storage = createStorage()

            shouldThrow<StorageException> {
                storage.list(ERROR_KEY).toList()
            }
        }

        "not wrap a CancellationException thrown by the provider" {
            val provider = mockk<StorageProvider> {
                every { list(any()) } returns flow { throw CancellationException("Test cancellation.") }
            }

            shouldThrow<CancellationException> {
                Storage(provider).list().toList()
            }
        }
    }
})

private const val STORAGE_TYPE = "testStorage"
private const val ERROR_KEY = "THROW!"
private const val CONTENT_TYPE = "test/content"

/** Create the [Config] for obtaining a [Storage] instance. */
private fun createStorageConfig(): Config =
    ConfigFactory.parseMap(
        mapOf(
            STORAGE_TYPE to mapOf(
                "name" to StorageProviderFactoryForTesting.NAME,
                StorageProviderFactoryForTesting.ERROR_KEY_PROPERTY to ERROR_KEY
            )
        )
    )

/** Return a [Storage] instance that can be used for testing. It is obtained via the default mechanism. */
private fun createStorage(): Storage =
    Storage.create(STORAGE_TYPE, ConfigManager.create(createStorageConfig()), mockk())
