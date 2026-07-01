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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.string.shouldContain

import io.mockk.mockk

class AnalyzerPhaseTest : WordSpec({
    "FullPhase" should {
        "throw if unexpected arguments are passed" {
            val phase = FullPhase(
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk(),
                mockk()
            )

            shouldThrow<IllegalArgumentException> {
                phase.run(mockk(), 42L, arrayOf("not-expected"))
            }
        }
    }

    "PreparationPhase" should {
        "throw if no exchange dir parameter is provided" {
            val phase = PreparationPhase(
                mockk(),
                mockk(),
                mockk()
            )

            shouldThrow<IllegalArgumentException> {
                phase.run(mockk(), 42L, emptyArray())
            }
        }

        "throw if an exchange dir parameter pointing to a non-existing directory is provided" {
            val nonExistingDir = "non-existing-directory"

            val phase = PreparationPhase(
                mockk(),
                mockk(),
                mockk()
            )

            val exception = shouldThrow<IllegalArgumentException> {
                phase.run(mockk(), 42L, arrayOf(nonExistingDir))
            }

            exception.message shouldContain nonExistingDir
        }

        "throw if too many arguments are provided" {
            val exchangeDir = tempdir()

            val phase = PreparationPhase(
                mockk(),
                mockk(),
                mockk()
            )

            shouldThrow<IllegalArgumentException> {
                phase.run(mockk(), 42L, arrayOf(exchangeDir.absolutePath, "extra-arg"))
            }
        }
    }
})
