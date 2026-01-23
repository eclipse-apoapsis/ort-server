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

package org.eclipse.apoapsis.ortserver.storage.s3

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.client.LogMode
import aws.smithy.kotlin.runtime.net.url.Url

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.storage.StorageProvider
import org.eclipse.apoapsis.ortserver.storage.StorageProviderFactory
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

/**
 * The implementation of the [StorageProviderFactory] interface for the AWS S3 storage. This factory creates a
 * [S3StorageProvider] to interact with the AWS S3 storage.
 */
class S3StorageProviderFactory : StorageProviderFactory {
    companion object {
        /** The name of this storage implementation. */
        const val NAME = "s3"

        /**
         * The name of the configuration property for the S3 storage access key.
         */
        const val ACCESS_KEY_PROPERTY = "s3AccessKey"

        /**
         * The name of the configuration property for the S3 storage secret key.
         */
        const val SECRET_KEY_PROPERTY = "s3SecretKey"

        /**
         * The name of the configuration property for the S3 storage region.
         */
        const val REGION_PROPERTY = "s3Region"

        /**
         * The name of the configuration property for the S3 bucket name.
         */
        const val BUCKET_NAME_PROPERTY = "s3BucketName"

        /**
         * The name of the configuration property for the endpoint URL of the S3 storage.
         */
        const val ENDPOINT_URL_PROPERTY = "s3EndpointUrl"
    }

    override val name: String = NAME

    /**
     * Create an instance of the [S3StorageProvider] using the provided [configuration][config].
     */
    override fun createProvider(config: ConfigManager): StorageProvider {
        val client = S3Client {
            region = config.getStringOrNull(REGION_PROPERTY)
            endpointUrl = Url.parse(config.getString(ENDPOINT_URL_PROPERTY))
            credentialsProvider = StaticCredentialsProvider {
                secretAccessKey = config.getSecret(Path(SECRET_KEY_PROPERTY))
                accessKeyId = config.getSecret(Path(ACCESS_KEY_PROPERTY))
            }

            logMode = LogMode.LogRequest + LogMode.LogResponse
        }

        return S3StorageProvider(client, config.getStringOrNull(BUCKET_NAME_PROPERTY))
    }
}
