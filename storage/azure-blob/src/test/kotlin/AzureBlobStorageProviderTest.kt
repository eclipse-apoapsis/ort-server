/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.storage.azureblob

import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClientBuilder

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.extensions.install
import io.kotest.core.spec.style.WordSpec
import io.kotest.extensions.testcontainers.ContainerExtension
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.Storage
import org.eclipse.apoapsis.ortserver.storage.Storage.Companion.dataString
import org.eclipse.apoapsis.ortserver.storage.StorageException

import org.testcontainers.containers.GenericContainer

private const val AZURITE_IMAGE = "mcr.microsoft.com/azure-storage/azurite:3.33.0"

// Default account name and key, see https://github.com/Azure/Azurite/blob/main/README.md#default-storage-account.
private const val ACCOUNT_NAME = "devstoreaccount1"
private const val ACCOUNT_KEY =
    "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw=="

private const val CONTAINER = "test"

class AzureBlobStorageProviderTest : WordSpec({
    val azuriteContainer =
        install(ContainerExtension(GenericContainer(AZURITE_IMAGE).apply { withExposedPorts(10000) }))

    lateinit var containerClient: BlobContainerClient

    beforeEach {
        val url = "http://${azuriteContainer.host}:${azuriteContainer.getMappedPort(10000)}"
        val connectionString = "DefaultEndpointsProtocol=http;" +
                "AccountName=$ACCOUNT_NAME;" +
                "AccountKey=$ACCOUNT_KEY;" +
                "BlobEndpoint=$url/devstoreaccount1;"

        val blobServiceClient = BlobServiceClientBuilder()
            .connectionString(connectionString)
            .buildClient()

        blobServiceClient.deleteBlobContainerIfExists(CONTAINER)
        blobServiceClient.createBlobContainer(CONTAINER)

        containerClient = blobServiceClient.getBlobContainerClient(CONTAINER)
    }

    "write" should {
        "add an object to the blob storage" {
            val storage = Storage(AzureBlobStorageProvider(containerClient))

            storage.write(Key("test"), "Hello, World!", "application/octet-stream")

            val blobClient = containerClient.getBlobClient("test")
            blobClient.exists() shouldBe true
            blobClient.downloadContent().toString() shouldBe "Hello, World!"
            blobClient.properties.contentType shouldBe "application/octet-stream"
        }

        "override an existing object in the blob storage" {
            val storage = Storage(AzureBlobStorageProvider(containerClient))

            storage.write(Key("test"), "Hello, World!", "application/octet-stream")
            storage.write(Key("test"), "Goodbye, World!", "application/octet-stream")

            val blobClient = containerClient.getBlobClient("test")
            blobClient.exists() shouldBe true
            blobClient.downloadContent().toString() shouldBe "Goodbye, World!"
            blobClient.properties.contentType shouldBe "application/octet-stream"
        }
    }

    "read" should {
        "read an existing object from the blob storage" {
            val storage = Storage(AzureBlobStorageProvider(containerClient))

            storage.write(Key("test"), "Hello, World!", "application/octet-stream")

            storage.read(Key("test")).use {
                it.dataString shouldBe "Hello, World!"
                it.contentType shouldBe "application/octet-stream"
            }
        }

        "throw an exception when reading a non-existing object from the blob storage" {
            val storage = Storage(AzureBlobStorageProvider(containerClient))

            shouldThrow<StorageException> {
                storage.read(Key("non-existing"))
            }
        }
    }

    "contains" should {
        "return false for a non-existing object" {
            val storage = Storage(AzureBlobStorageProvider(containerClient))

            storage.containsKey(Key("non-existing")) shouldBe false
        }

        "return true for an existing object" {
            val storage = Storage(AzureBlobStorageProvider(containerClient))

            storage.write(Key("test"), "Hello, World!", "application/octet-stream")

            storage.containsKey(Key("test")) shouldBe true
        }
    }
})
