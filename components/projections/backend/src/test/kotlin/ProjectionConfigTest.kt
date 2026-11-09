/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.projections

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ProjectionConfigTest : WordSpec({
    "create" should {
        "read the configured values" {
            val config = ConfigFactory.parseMap(
                mapOf(
                    "projections.leaseDurationSeconds" to 60,
                    "projections.maxParallelBuilds" to 4,
                    "projections.recoveryIntervalSeconds" to 10,
                    "projections.maxAttempts" to 5,
                    "projections.retryDelaySeconds" to 0,
                    "projections.asyncRequestWaitMillis" to 0,
                    "projections.syncRequestWaitMillis" to 500
                )
            )

            ProjectionConfig.create(config) shouldBe ProjectionConfig(
                leaseDuration = 1.minutes,
                maxParallelBuilds = 4,
                recoveryInterval = 10.seconds,
                maxAttempts = 5,
                retryDelay = Duration.ZERO,
                asyncRequestWait = Duration.ZERO,
                syncRequestWait = 500.milliseconds
            )
        }

        "use the default values for missing properties" {
            ProjectionConfig.create(ConfigFactory.empty()) shouldBe ProjectionConfig()
        }

        "reject a recovery interval that is not positive" {
            val config = ConfigFactory.parseMap(mapOf("projections.recoveryIntervalSeconds" to 0))

            shouldThrow<IllegalArgumentException> { ProjectionConfig.create(config) }
        }
    }

    "validate" should {
        "accept the default values" {
            shouldNotThrowAny { ProjectionConfig().validate() }
        }

        "reject invalid values" {
            shouldThrow<IllegalArgumentException> { ProjectionConfig(leaseDuration = Duration.ZERO).validate() }
            shouldThrow<IllegalArgumentException> { ProjectionConfig(leaseDuration = Duration.INFINITE).validate() }
            shouldThrow<IllegalArgumentException> { ProjectionConfig(maxParallelBuilds = 0).validate() }
            shouldThrow<IllegalArgumentException> { ProjectionConfig(recoveryInterval = (-1).seconds).validate() }
            shouldThrow<IllegalArgumentException> { ProjectionConfig(recoveryInterval = Duration.INFINITE).validate() }
            shouldThrow<IllegalArgumentException> { ProjectionConfig(maxAttempts = 0).validate() }
            shouldThrow<IllegalArgumentException> { ProjectionConfig(retryDelay = (-1).seconds).validate() }
            shouldThrow<IllegalArgumentException> { ProjectionConfig(asyncRequestWait = (-1).milliseconds).validate() }
            shouldThrow<IllegalArgumentException> { ProjectionConfig(syncRequestWait = (-1).milliseconds).validate() }
        }
    }
})
