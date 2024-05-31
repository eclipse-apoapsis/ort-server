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

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.model.orchestrator.OrchestratorMessage
import org.eclipse.apoapsis.ortserver.transport.Message
import org.eclipse.apoapsis.ortserver.transport.MessageReceiverFactory
import org.eclipse.apoapsis.ortserver.transport.OrchestratorEndpoint

/**
 * Start a receiver that is initialized from the given [configManager]. Since the receiver blocks, this has to be done
 * in a separate thread. Return a queue that can be polled to obtain the received messages.
 */
fun startReceiver(configManager: ConfigManager): LinkedBlockingQueue<Message<OrchestratorMessage>> {
    val queue = LinkedBlockingQueue<Message<OrchestratorMessage>>()

    fun handler(message: Message<OrchestratorMessage>) {
        queue.offer(message)
    }

    Thread {
        MessageReceiverFactory.createReceiver(OrchestratorEndpoint, configManager, ::handler)
    }.start()

    return queue
}

/**
 * Check that the next message in this queue has the given [token], [traceId], [runId], and [payload].
 */
fun <T> BlockingQueue<Message<T>>.checkMessage(token: String, traceId: String, runId: Long, payload: T) {
    val message = poll(5, TimeUnit.SECONDS)

    message.shouldNotBeNull()
    message.header.token shouldBe token
    message.header.traceId shouldBe traceId
    message.header.ortRunId shouldBe runId
    message.payload shouldBe payload
}
