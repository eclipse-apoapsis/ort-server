/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.transport.azureservicebus

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.ServiceBusErrorContext
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode

import kotlinx.coroutines.CompletableDeferred

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.EndpointHandlerResult
import org.eclipse.apoapsis.ortserver.transport.MessageReceiverFactory
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

import org.slf4j.LoggerFactory
import org.slf4j.MDC

private val logger = LoggerFactory.getLogger(AzureServicebusMessageReceiverFactory::class.java)

/**
 * Implementation of the [MessageReceiverFactory] interface for Azure Servicebus.
 */
class AzureServicebusMessageReceiverFactory : MessageReceiverFactory {
    override val name = AzureServicebusConfig.TRANSPORT_NAME

    override suspend fun <T : Any> createReceiver(
        from: Endpoint<T>,
        configManager: ConfigManager,
        handler: EndpointHandler<T>
    ) {
        val serializer = JsonSerializer.forClass(from.messageClass)
        val stopSignal = CompletableDeferred<Unit>()

        fun processMessage(context: ServiceBusReceivedMessageContext) {
            val message = AzureServicebusMessageConverter.toTransportMessage(context.message, serializer)

            MDC.put("traceId", message.header.traceId)
            MDC.put("ortRunId", message.header.ortRunId.toString())

            logger.debug(
                "Received message '${message.header.traceId}' with payload of type '${message.payload.javaClass.name}'."
            )

            runBlocking {
                if (handler(message) == EndpointHandlerResult.STOP) {
                    logger.info("Stopping message receiver for endpoint '${from.configPrefix}'.")
                    stopSignal.complete(Unit)
                }
            }
        }

        fun processError(context: ServiceBusErrorContext) {
            val exception = context.exception
            logger.warn("Error processing message: ${exception.message}", exception)
        }

        val config = AzureServicebusConfig.createConfig(configManager)
        val credential = DefaultAzureCredentialBuilder().build()

        val client = ServiceBusClientBuilder()
            .fullyQualifiedNamespace("${config.namespace}.servicebus.windows.net")
            .credential(credential)
            .processor()
            .queueName(config.queueName)
            .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE)
            .processMessage(::processMessage)
            .processError(::processError)
            .maxConcurrentCalls(1)
            .prefetchCount(0)
            .buildProcessorClient()

        client.use {
            it.start()
            stopSignal.await()
            it.stop()
        }
    }
}
