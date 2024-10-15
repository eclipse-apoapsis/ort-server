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

package org.eclipse.apoapsis.ortserver.transport.azureservicebus

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.ServiceBusErrorContext
import com.azure.messaging.servicebus.ServiceBusProcessorClient
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.EndpointHandler
import org.eclipse.apoapsis.ortserver.transport.MessageReceiverFactory
import org.eclipse.apoapsis.ortserver.transport.json.JsonSerializer
import org.eclipse.apoapsis.ortserver.utils.logging.runBlocking

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.lang.Exception
import java.lang.System.exit
import kotlin.coroutines.coroutineContext
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger(AzureServicebusMessageReceiverFactory::class.java)

/**
 * Implementation of the [MessageReceiverFactory] interface for Azure Servicebus.
 */
class AzureServicebusMessageReceiverFactory : MessageReceiverFactory {
    override val name = "azure-servicebus"

    override suspend fun <T : Any> createReceiver(
        from: Endpoint<T>,
        configManager: ConfigManager,
        handler: EndpointHandler<T>
    ) {
        val serializer = JsonSerializer.forClass(from.messageClass)

        val config = AzureServicebusConfig.createConfig(configManager)

        fun processMessage(context: ServiceBusReceivedMessageContext) {
            val message = AzureServicebusMessageConverter.toTransportMessage(context.message, serializer)

            MDC.put("traceId", message.header.traceId)
            MDC.put("ortRunId", message.header.ortRunId.toString())

            if (logger.isDebugEnabled) {
                logger.debug(
                    "Received message '${message.header.traceId}' with payload of type " +
                            "'${message.payload.javaClass.name}'."
                )
            }

            runBlocking {
                try {
                    handler(message)
                } catch (e: Exception) {
                    logger.error("Message processing caused an exception.", e)
                    if (config.singleMessage) {
                        exitProcess(1)
                        // TODO: It seems that above call causes the message to not be completed.
                        //       Maybe change the receive mode?
                    }
                } finally {
                    if (config.singleMessage) {
                        //context.complete()
                        exitProcess(0)
                        // TODO: It seems that above call causes the message to not be completed.
                    }
                }

//                if (config.singleMessage) {
//                    client.stop()
//                    coroutineContext.cancel() // TODO: This does not work as expected, the client is not stopped.
//                }
            }
        }

        fun processError(context: ServiceBusErrorContext) {
            val exception = context.exception
            logger.warn("Error processing message: ${exception.message}", exception)
        }

        val credential = DefaultAzureCredentialBuilder().build()

        val client = ServiceBusClientBuilder()
            .fullyQualifiedNamespace("${config.namespace}.servicebus.windows.net")
            .credential(credential)
            .processor()
            .disableAutoComplete()
            .queueName(config.queueName)
            .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE) // TODO: maybe use this?
            .processMessage(::processMessage)
            .processError(::processError)
            .buildProcessorClient()

        client.use {
            it.start()
            awaitCancellation()
        }
    }
}
