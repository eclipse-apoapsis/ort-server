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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AnalyzerPhase
import org.eclipse.apoapsis.ortserver.transport.EndpointComponent

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

    "AnalysisPhase" should {
        "throw if too many arguments are provided" {
            val exchangeDir = tempdir()

            val phase = AnalysisPhase()

            shouldThrow<IllegalArgumentException> {
                phase.run(mockk(), 42L, arrayOf(exchangeDir.absolutePath, "sync", "extra-arg"))
            }
        }
    }

    "ResultPhase" should {
        "throw if too many arguments are provided" {
            val exchangeDir = tempdir()

            val phase = ResultPhase(mockk(), mockk(), mockk(), mockk(), mockk())

            shouldThrow<IllegalArgumentException> {
                phase.run(mockk(), 42L, arrayOf(exchangeDir.absolutePath, "sync", "extra-arg"))
            }
        }
    }

    "handleKeepAlive()" should {
        "write a keep-alive file if the phase matches" {
            val config = AnalyzerJobConfiguration(
                keepAliveWorker = true,
                keepAlivePhases = EnumSet.of(AnalyzerPhase.PREPARATION, AnalyzerPhase.ANALYSIS)
            )
            val phase = AnalysisPhase()

            mockkObject(EndpointComponent) {
                coEvery { EndpointComponent.generateKeepAliveFile() } just runs

                phase.handleKeepAlive(config)

                coVerify { EndpointComponent.generateKeepAliveFile() }
            }
        }

        "not write a keep-alive file if the phase does not match" {
            val config = AnalyzerJobConfiguration(
                keepAliveWorker = true,
                keepAlivePhases = EnumSet.of(AnalyzerPhase.PREPARATION, AnalyzerPhase.RESULT)
            )
            val phase = AnalysisPhase()

            mockkObject(EndpointComponent) {
                phase.handleKeepAlive(config)

                coVerify(exactly = 0) { EndpointComponent.generateKeepAliveFile() }
            }
        }

        "not write a keep-alive file if the keep-alive flag is not set" {
            val config = AnalyzerJobConfiguration(
                keepAliveWorker = false,
                keepAlivePhases = EnumSet.of(AnalyzerPhase.ANALYSIS)
            )
            val phase = AnalysisPhase()

            mockkObject(EndpointComponent) {
                phase.handleKeepAlive(config)

                coVerify(exactly = 0) { EndpointComponent.generateKeepAliveFile() }
            }
        }

        "write a keep-alive file per default for the full phase" {
            val config = AnalyzerJobConfiguration(
                keepAliveWorker = true
            )
            val phase = FullPhase(
                db = mockk(),
                ortRunService = mockk(),
                contextFactory = mockk(),
                environmentService = mockk(),
                adminConfigService = mockk(),
                issueResolutionService = mockk()
            )

            mockkObject(EndpointComponent) {
                coEvery { EndpointComponent.generateKeepAliveFile() } just runs

                phase.handleKeepAlive(config)

                coVerify { EndpointComponent.generateKeepAliveFile() }
            }
        }

        "write a keep-alive file per default for the analysis phase" {
            val config = AnalyzerJobConfiguration(
                keepAliveWorker = true,
                keepAlivePhases = emptySet()
            )
            val phase = AnalysisPhase()

            mockkObject(EndpointComponent) {
                coEvery { EndpointComponent.generateKeepAliveFile() } just runs

                phase.handleKeepAlive(config)

                coVerify { EndpointComponent.generateKeepAliveFile() }
            }
        }
    }
})
