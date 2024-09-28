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

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.storage.blob.BlobServiceClientBuilder

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.storage.StorageProvider
import org.eclipse.apoapsis.ortserver.storage.StorageProviderFactory
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

class AzureBlobStorageProviderFactory : StorageProviderFactory {
    companion object {
        const val CONTAINER_NAME_PROPERTY = "azureBlobContainerName"
        const val ENDPOINT_URL_PROPERTY = "azureBlobEndpointUrl"
        const val STORAGE_ACCOUNT_NAME_PROPERTY = "azureBlobStorageAccountName"
    }

    override val name = "azure-blob"

    override fun createProvider(config: ConfigManager): StorageProvider {
        val defaultCredential = DefaultAzureCredentialBuilder().build()

        val endpointUrl = config.getStringOrNull(ENDPOINT_URL_PROPERTY)
            ?: "https://${config.getString(STORAGE_ACCOUNT_NAME_PROPERTY)}.blob.core.windows.net/"

        val blobServiceClient = BlobServiceClientBuilder()
            .endpoint(endpointUrl)
            .credential(defaultCredential)
            .buildClient()

        val blobContainerClient = blobServiceClient
            .getBlobContainerClient(config.getString(CONTAINER_NAME_PROPERTY))

        return AzureBlobStorageProvider(blobContainerClient)
    }
}
