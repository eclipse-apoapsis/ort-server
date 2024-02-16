/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport.artemis

import com.typesafe.config.ConfigFactory

import io.kotest.core.extensions.install
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.ContainerExtension

import org.ossreviewtoolkit.server.config.ConfigManager

import org.slf4j.LoggerFactory

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer

private const val ARTEMIS_CONTAINER = "quay.io/artemiscloud/activemq-artemis-broker:artemis.2.26.0"
private const val ARTEMIS_PORT = 61616

/**
 * Extension function to start an Artemis broker in a test container for testing a factory of the given
 * [transportType]. The resulting [ConfigManager] can be used to connect to the broker in the container.
 */
fun Spec.startArtemisContainer(transportType: String): ConfigManager {
    val containerEnv = mapOf("AMQ_USER" to "admin", "AMQ_PASSWORD" to "admin")
    val artemisContainer = install(
        ContainerExtension(
            GenericContainer(ARTEMIS_CONTAINER).apply {
                startupAttempts = 1
                withExposedPorts(ARTEMIS_PORT)
                withEnv(containerEnv)
                withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("artemis")))
            }
        )
    )

    val keyPrefix = "orchestrator.$transportType"
    val configMap = mapOf(
        "$keyPrefix.serverUri" to "amqp://${artemisContainer.host}:${artemisContainer.firstMappedPort}",
        "$keyPrefix.queueName" to "testQueue",
        "$keyPrefix.type" to "activeMQ"
    )
    return ConfigManager.create(ConfigFactory.parseMap(configMap))
}
