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

package org.eclipse.apoapsis.ortserver.logaccess

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

import org.eclipse.apoapsis.ortserver.model.LogLevel

class LogLevelTest : WordSpec({
    "levelOrHigher" should {
        "return a correct set for the ERROR level" {
            LogLevel.levelOrHigher(LogLevel.ERROR) shouldContainExactlyInAnyOrder setOf(LogLevel.ERROR)
        }

        "return a correct set for the WARN level" {
            LogLevel.levelOrHigher(LogLevel.WARN) shouldContainExactlyInAnyOrder setOf(
                LogLevel.ERROR,
                LogLevel.WARN
            )
        }

        "return a correct set for the INFO level" {
            LogLevel.levelOrHigher(LogLevel.INFO) shouldContainExactlyInAnyOrder setOf(
                LogLevel.ERROR,
                LogLevel.WARN,
                LogLevel.INFO
            )
        }

        "return a correct set for the DEBUG level" {
            LogLevel.levelOrHigher(LogLevel.DEBUG) shouldContainExactlyInAnyOrder setOf(
                LogLevel.ERROR,
                LogLevel.WARN,
                LogLevel.INFO,
                LogLevel.DEBUG
            )
        }
    }
})
