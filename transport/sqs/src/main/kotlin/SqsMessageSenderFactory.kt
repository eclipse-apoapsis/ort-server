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

import aws.sdk.kotlin.services.sqs.model.GetQueueUrlRequest

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.MessageSender
import org.eclipse.apoapsis.ortserver.transport.MessageSenderFactory
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(SqsMessageSenderFactory::class.java)

/**
 * A [MessageSenderFactory] implementation for AWS SQS.
 * Also see https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/kotlin_sqs_code_examples.html.
 */
class SqsMessageSenderFactory : MessageSenderFactory {
    override val name: String = SqsConfig.TRANSPORT_NAME

    override fun <T : Any> createSender(to: Endpoint<T>, configManager: ConfigManager): MessageSender<T> {
        logger.info("Creating SQS sender for endpoint '${to.configPrefix}'.")

        val config = SqsConfig.create(configManager)
        val client = createSqsClient(config)

        val response = runCatching {
            runBlocking { client.getQueueUrl(GetQueueUrlRequest { queueName = config.queueName }) }
        }.onFailure {
            client.close()
        }.getOrThrow()

        val queueUrl = checkNotNull(response.queueUrl) {
            "The SQS queue URL for queue '$name' must not be null."
        }

        return SqsMessageSender(client, queueUrl, to)
    }
}
