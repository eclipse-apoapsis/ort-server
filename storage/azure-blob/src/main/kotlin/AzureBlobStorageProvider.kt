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

import com.azure.core.util.BinaryData
import com.azure.core.util.Context
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.models.BlobHttpHeaders
import com.azure.storage.blob.models.BlobRequestConditions
import com.azure.storage.blob.options.BlobParallelUploadOptions

import java.io.InputStream

import org.eclipse.apoapsis.ortserver.storage.Key
import org.eclipse.apoapsis.ortserver.storage.StorageEntry
import org.eclipse.apoapsis.ortserver.storage.StorageProvider

class AzureBlobStorageProvider(private val blobContainerClient: BlobContainerClient) : StorageProvider {
    override suspend fun read(key: Key): StorageEntry {
        val blobClient = blobContainerClient.getBlobClient(key.key)

        return StorageEntry.create(
            data = blobClient.openInputStream(),
            contentType = blobClient.blockBlobClient.properties.contentType,
            length = blobClient.blockBlobClient.properties.blobSize
        )
    }

    override suspend fun write(key: Key, data: InputStream, length: Long, contentType: String?) {
        val blobClient = blobContainerClient.getBlobClient(key.key)

        val headers = BlobHttpHeaders().setContentType(contentType)
        val binaryData = BinaryData.fromStream(data, length)
        val options = BlobParallelUploadOptions(binaryData)
            .setRequestConditions(BlobRequestConditions())
            .setHeaders(headers)

        blobClient.uploadWithResponse(options, /* timeout = */ null, Context.NONE)
    }

    override suspend fun contains(key: Key): Boolean = blobContainerClient.getBlobClient(key.key).exists()

    override suspend fun delete(key: Key): Boolean = blobContainerClient.getBlobClient(key.key).deleteIfExists()
}
