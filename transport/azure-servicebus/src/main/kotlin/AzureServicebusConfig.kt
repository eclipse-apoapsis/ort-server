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

import org.eclipse.apoapsis.ortserver.config.ConfigManager

/**
 * A class defining the configuration settings used by the Azure Service Bus Transport implementation.
 */
class AzureServicebusConfig(
    /** The namespace of the Azure Service Bus. */
    val namespace: String,

    /** The name of the queue that is used for sending and receiving messages. */
    val queueName: String
) {
    companion object {
        /**
         * Constant for the name of this transport implementation. This name is used for both the message sender and
         * receiver factories.
         */
        const val TRANSPORT_NAME = "azure-servicebus"

        /** Name of the configuration property for the namespace. */
        private const val NAMESPACE_PROPERTY = "namespace"

        /** Name of the configuration property for the queue name. */
        private const val QUEUE_NAME_PROPERTY = "queueName"

        /**
         * Create a [AzureServicebusConfig] from the provided [configManager].
         */
        fun createConfig(configManager: ConfigManager) =
            AzureServicebusConfig(
                namespace = configManager.getString(NAMESPACE_PROPERTY),
                queueName = configManager.getString(QUEUE_NAME_PROPERTY),
            )
    }
}
