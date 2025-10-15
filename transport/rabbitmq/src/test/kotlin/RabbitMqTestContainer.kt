/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.transport.rabbitmq

import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.TestContainerSpecExtension

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.transport.testing.createConfigManager

import org.testcontainers.rabbitmq.RabbitMQContainer

/**
 * Extension function to start an RabbitMq broker in a test container for testing a factory of the given [consumerName]
 * and [transportType]. The resulting [ConfigManager] can be used to connect to the broker in the container.
 */
fun Spec.startRabbitMqContainer(consumerName: String, transportType: String): ConfigManager {
    val rabbitMq = install(
        TestContainerSpecExtension(
            RabbitMQContainer("rabbitmq")
        )
    )

    val secretsMap = mapOf(
        "rabbitMqUser" to "guest",
        "rabbitMqPassword" to "guest"
    )

    val configProvidersMap = mapOf(
        ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME,
        ConfigSecretProviderFactoryForTesting.SECRETS_PROPERTY to secretsMap
    )

    return createConfigManager(
        consumerName = consumerName,
        transportType = transportType,
        transportName = "rabbitMQ",
        serverUri = "amqp://${rabbitMq.host}:${rabbitMq.firstMappedPort}",
        configProvidersMap = configProvidersMap
    )
}
