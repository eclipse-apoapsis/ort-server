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

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

import java.util.concurrent.atomic.AtomicInteger

import kotlin.time.Duration.Companion.milliseconds

import kotlinx.coroutines.delay

class SchedulerTest : WordSpec({
    "schedule" should {
        "schedule an action" {
            val counter = AtomicInteger()

            Scheduler().use { scheduler ->
                scheduler.schedule(10.milliseconds) {
                    counter.incrementAndGet()
                }

                eventually(1000.milliseconds) {
                    counter.get() shouldBeGreaterThan 1
                }
            }
        }

        "handle exceptions thrown by the action" {
            val counter = AtomicInteger()

            Scheduler().use { scheduler ->
                scheduler.schedule(10.milliseconds) {
                    require(counter.incrementAndGet() > 1) {
                        "Test exception from action."
                    }
                }

                eventually(1000.milliseconds) {
                    counter.get() shouldBeGreaterThan 2
                }
            }
        }
    }

    "close" should {
        "terminate all scheduled actions" {
            val counter = AtomicInteger()
            val scheduler = Scheduler()

            val action: SchedulerAction = {
                counter.incrementAndGet()
            }

            scheduler.schedule(10.milliseconds, action)
            scheduler.schedule(15.milliseconds, action)
            delay(20)

            scheduler.close()
            val currentValue = counter.get()

            delay(20)
            counter.get() shouldBe currentValue
        }
    }
})
