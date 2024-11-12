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

package org.eclipse.apoapsis.ortserver.kubernetes.jobmonitor

import kotlin.time.Duration

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(Scheduler::class.java)

/**
 * Type alias for actions that can be scheduled.
 */
typealias SchedulerAction = suspend () -> Unit

/**
 * A helper class for scheduling actions periodically.
 *
 * This class is used to trigger the components of this module that do periodic checks. Note that this is not a
 * general-purpose scheduler implementation, but focused to the use case at hand.
 */
internal class Scheduler : AutoCloseable {
    /** The [CoroutineScope] to use for scheduling. */
    private val coroutineScope = CoroutineScope(Dispatchers.IO + MDCContext())

    /**
     * Schedule the given [action] to be executed periodically in the given [interval].
     */
    fun schedule(interval: Duration, action: SchedulerAction) {
        val tickerFlow = createTickerFlow(interval)

        coroutineScope.launch {
            tickerFlow.collect {
                runCatching {
                    action()
                }.onFailure { e ->
                    logger.error("Exception while executing scheduled action.", e)
                }
            }
        }
    }

    /**
     * Close this scheduler and terminate all scheduled actions.
     */
    override fun close() {
        coroutineScope.coroutineContext.cancel()
    }
}

/**
 * Return a [Flow] that produces periodic tick events in the given [interval]. Based on this, actions are triggered
 * by the [Scheduler].
 */
private fun createTickerFlow(interval: Duration): Flow<Unit> = flow {
    while (true) {
        delay(interval)
        emit(Unit)
    }
}
