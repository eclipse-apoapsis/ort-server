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

package org.eclipse.apoapsis.ortserver.transport.testing

import com.typesafe.config.ConfigFactory

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import kotlin.concurrent.thread

import kotlinx.coroutines.runBlocking

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.transport.EndpointHandlerResult
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageReceiverFactory
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint

const val TEST_QUEUE_NAME = "test_queue"
const val TEST_QUEUE_TIMEOUT = 30L

/**
 * Create a [ConfigManager] with a test queue for [consumerName] using [transportType] and [transportName] that is
 * accessible at the given [serverUri] with optional [configProvidersMap].
 */
fun createConfigManager(
    consumerName: String,
    transportType: String,
    transportName: String,
    serverUri: String,
    configProvidersMap: Map<String, Any> = emptyMap()
): ConfigManager {
    val configMap = buildMap {
        put("$consumerName.$transportType.type", transportName)
        put("$consumerName.$transportType.serverUri", serverUri)
        put("$consumerName.$transportType.queueName", TEST_QUEUE_NAME)
        if (configProvidersMap.isNotEmpty()) put(ConfigManager.CONFIG_MANAGER_SECTION, configProvidersMap)
    }

    return ConfigManager.create(ConfigFactory.parseMap(configMap))
}

/**
 * Start a receiver that is initialized from the given [configManager]. Since the receiver blocks, this has to be done
 * in a separate thread. Return a queue that can be polled to obtain the received messages.
 */
fun startReceiver(
    configManager: ConfigManager,
    result: EndpointHandlerResult = EndpointHandlerResult.CONTINUE
): LinkedBlockingQueue<Message<OrchestratorMessage>> {
    val queue = LinkedBlockingQueue<Message<OrchestratorMessage>>()

    fun handler(message: Message<OrchestratorMessage>): EndpointHandlerResult {
        queue.offer(message)
        return result
    }

    thread {
        @Suppress("ForbiddenMethodCall")
        runBlocking {
            MessageReceiverFactory.createReceiver(OrchestratorEndpoint, configManager, ::handler)
        }
    }

    return queue
}

/**
 * Check that the next message in this queue has the given [traceId], [runId], and [payload].
 */
fun <T> BlockingQueue<Message<T>>.checkMessage(traceId: String, runId: Long, payload: T) {
    poll(TEST_QUEUE_TIMEOUT, TimeUnit.SECONDS) shouldNotBeNull {
        header.traceId shouldBe traceId
        header.ortRunId shouldBe runId
        this.payload shouldBe payload
    }
}
