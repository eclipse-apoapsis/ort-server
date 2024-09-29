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

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.transport.Endpoint
import org.eclipse.apoapsis.ortserver.transport.MessageSender
import org.eclipse.apoapsis.ortserver.transport.MessageSenderFactory

/**
 * Implementation of the [MessageSenderFactory] interface for Azure Servicebus.
 */
class AzureServicebusMessageSenderFactory : MessageSenderFactory {
    override val name = "azure-servicebus"

    override fun <T : Any> createSender(to: Endpoint<T>, configManager: ConfigManager): MessageSender<T> {
        val config = AzureServicebusConfig.createConfig(configManager)
        val credential = DefaultAzureCredentialBuilder().build();

        val client = ServiceBusClientBuilder()
            .fullyQualifiedNamespace("${config.namespace}.servicebus.windows.net")
            .credential(credential)
            .sender()
            .queueName(config.queueName)
            .buildClient()

        return AzureServicebusMessageSender(client, to)
    }
}
