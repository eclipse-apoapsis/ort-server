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

package org.eclipse.apoapsis.ortserver.tasks.impl.kubernetes

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk

import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class TimeHelperTest : WordSpec({
    "now" should {
        "return the time from the provided clock" {
            val currentTime = Instant.parse("2024-08-07T10:58:42Z")
            val clock = mockk<Clock>()
            every { clock.now() } returns currentTime

            val timeHelper = TimeHelper(clock)

            timeHelper.now() shouldBe currentTime
        }

        "use the system clock per default" {
            val timeHelper = TimeHelper()

            val currentTime = timeHelper.now()

            val systemTime = Clock.System.now()
            systemTime - currentTime shouldBeLessThan 1000.milliseconds
        }
    }

    "before" should {
        "compute a correct timestamp in the past" {
            val currentTime = Instant.parse("2024-08-07T11:20:38Z")
            val zoneOffset = ZoneOffset.ofHours(2)
            val clock = mockk<Clock>()
            every { clock.now() } returns currentTime

            val timeHelper = TimeHelper(clock, zoneOffset)

            timeHelper.before(10.minutes) shouldBe OffsetDateTime.of(
                2024,
                Month.AUGUST.value,
                7,
                13,
                10,
                38,
                0,
                zoneOffset
            )
        }
    }
})
