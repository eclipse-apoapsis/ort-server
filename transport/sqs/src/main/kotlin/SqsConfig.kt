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

package org.eclipse.apoapsis.ortserver.transport.sqs

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.sqs.SqsClient
import aws.smithy.kotlin.runtime.net.url.Url

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path

internal const val SERVER_URI_PROP_NAME = "serverUri"
internal const val ACCESS_KEY_ID_PROP_NAME = "accessKeyId"
internal const val SECRET_ACCESS_KEY_PROP_NAME = "secretAccessKey"
internal const val QUEUE_NAME_PROP_NAME = "queueName"

/**
 * This class defines the configuration properties for an SQS transport. While any AWS-compatible implementation of SQS
 * should be supported, including AWS itself and e.g., LocalStack, this specifically targets the implementation at
 * Scaleway for now, see https://www.scaleway.com/en/developers/api/messaging-and-queuing/sqs-api/.
 */
data class SqsConfig(
    val serverUri: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val queueName: String
) {
    companion object {
        const val TRANSPORT_NAME = "SQS"

        fun create(configManager: ConfigManager) =
            SqsConfig(
                serverUri = configManager.getString(SERVER_URI_PROP_NAME),
                accessKeyId = configManager.getSecret(Path(ACCESS_KEY_ID_PROP_NAME)),
                secretAccessKey = configManager.getSecret(Path(SECRET_ACCESS_KEY_PROP_NAME)),
                queueName = configManager.getString(QUEUE_NAME_PROP_NAME)
            )
    }
}

/**
 * Create an AWS [SqsClient] from ORT [SqsConfig].
 */
internal fun createSqsClient(config: SqsConfig) =
    SqsClient {
        // Although the region gets overridden by the endpoint URL, hard-code it to some nun-null value to avoid an
        // error from the AWS SDK saying "No instance for AttributeKey(aws.smithy.kotlin.signing#AwsSigningRegion)".
        // For the fun of it, use the name for the "Europe (Paris)" region, which is the equivalent for Scaleway's
        // default "fr-par" region.
        region = "eu-west-3"
        endpointUrl = Url.parse(config.serverUri)

        credentialsProvider = StaticCredentialsProvider {
            accessKeyId = config.accessKeyId
            secretAccessKey = config.secretAccessKey
        }
    }
