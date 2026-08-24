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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

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
 * A handle to a receiver started via [startReceiver]. It acts as the [BlockingQueue] the received messages are put
 * into, and additionally allows to [stop] the receiver again.
 */
class ReceiverHandle internal constructor(
    private val job: Job,
    queue: BlockingQueue<Message<OrchestratorMessage>>
) : BlockingQueue<Message<OrchestratorMessage>> by queue {
    /**
     * Stop this receiver and wait until it has terminated. Tests that share a queue between test cases must do this
     * at the end of each test case; otherwise the still running receiver of an already finished test case competes
     * for the messages that are meant for the next test case.
     *
     * This requires a receiver that suspends while waiting for messages. That is the case for SQS, which checks
     * `coroutineContext.isActive` in its receive loop, and for RabbitMQ, which collects a cancellable flow. It does
     * not work for Artemis, whose receive loop does not check whether it is still active and blocks in a JMS
     * `receive()` call that cannot be interrupted from the outside.
     */
    suspend fun stop() {
        job.cancelAndJoin()
    }
}

/**
 * Start a receiver that is initialized from the given [configManager]. Since the receiver runs until it is stopped,
 * this has to be done in a separate coroutine. Return a [ReceiverHandle] that can be polled to obtain the received
 * messages and that allows to stop the receiver again.
 */
fun startReceiver(
    configManager: ConfigManager,
    result: EndpointHandlerResult = EndpointHandlerResult.CONTINUE
): ReceiverHandle {
    val queue = LinkedBlockingQueue<Message<OrchestratorMessage>>()

    fun handler(message: Message<OrchestratorMessage>): EndpointHandlerResult {
        queue.offer(message)
        return result
    }

    val job = CoroutineScope(Dispatchers.IO).launch {
        MessageReceiverFactory.createReceiver(OrchestratorEndpoint, configManager, ::handler)
    }

    return ReceiverHandle(job, queue)
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
