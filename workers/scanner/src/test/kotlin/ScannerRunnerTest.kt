/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.scanner

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.scanner.ScannerWrapper
import org.ossreviewtoolkit.scanner.scanners.scancode.ScanCode
import org.ossreviewtoolkit.server.model.ScannerJobConfiguration

class ScannerRunnerTest : WordSpec({
    val runner = ScannerRunner(mockk(), mockk(), mockk())

    "run" should {
        "return a OrtResult with a valid ScannerRun" {
            mockkObject(ScannerWrapper)
            mockk<ScannerWrapper> {
                every { ScannerWrapper.ALL } returns sortedMapOf(
                    "ScanCode" to mockk {
                        every { create(any(), any()) } returns mockk<ScanCode> {
                            every { criteria } returns mockk {
                                every { matches(any()) } returns true
                            }
                            every { details } returns mockk()
                        }
                    }
                )
            }

            val result = runner.run(OrtResult.EMPTY, ScannerJobConfiguration())

            val scannerRun = result.scanner.shouldNotBeNull()
            scannerRun.scanResults shouldBe emptyMap()
        }
    }
})
