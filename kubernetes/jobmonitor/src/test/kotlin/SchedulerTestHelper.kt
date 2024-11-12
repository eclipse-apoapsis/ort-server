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

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

import kotlin.time.Duration

/**
 * A test helper class that simplifies tests for components using a [Scheduler] object. The class provides a mock
 * [Scheduler] and allows access to an action that has been scheduled on it.
 */
internal class SchedulerTestHelper {
    /** The mock [Scheduler] managed by this test helper. */
    val scheduler = createSchedulerMock()

    /**
     * The action that was scheduled on the [scheduler]. This is only available after calling [expectSchedule].
     */
    private lateinit var action: SchedulerAction

    /**
     * Expect a schedule invocation on the managed mock [Scheduler] with the given [interval]. Store the passed action,
     * so that it can later be triggered.
     */
    fun expectSchedule(interval: Duration): SchedulerTestHelper {
        action = fetchScheduledAction(scheduler, interval)
        return this
    }

    /**
     * Invoke the action scheduled on the managed [Scheduler] for the given number of [times]. Note that
     * [expectSchedule] must have been called before.
     */
    suspend fun triggerAction(times: Int = 1): SchedulerTestHelper {
        repeat(times) {
            action()
        }

        return this
    }
}

/**
 * Create a mock [Scheduler] that is prepared to expect arbitrary calls to [Scheduler.schedule].
 */
internal fun createSchedulerMock(): Scheduler =
    initSchedulerMock(mockk())

/**
 * Prepare the given [scheduler] to expect arbitrary calls to [Scheduler.schedule].
 */
internal fun initSchedulerMock(scheduler: Scheduler): Scheduler {
    every { scheduler.schedule(any(), any()) } just runs
    return scheduler
}

/**
 * Verify that an action was scheduled on the given [scheduler] with the given [interval] and return the action.
 */
internal fun fetchScheduledAction(scheduler: Scheduler, interval: Duration): SchedulerAction {
    val slot = slot<SchedulerAction>()
    verify { scheduler.schedule(interval, capture(slot)) }
    return slot.captured
}
