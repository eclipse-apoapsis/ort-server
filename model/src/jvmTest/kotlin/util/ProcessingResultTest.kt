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

package org.eclipse.apoapsis.ortserver.model.util

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class ProcessingResultTest : WordSpec({
    "successCount" should {
        "return the total count if there are no failed items" {
            val result = ProcessingResult(totalCount = 10, failedCount = 0)

                result.successCount shouldBe 10
        }

        "return zero if all items failed" {
            val result = ProcessingResult(totalCount = 10, failedCount = 10)

                result.successCount shouldBe 0
        }

        "return the total count minus the failed count if there are failed items" {
            val result = ProcessingResult(totalCount = 10, failedCount = 3)

                result.successCount shouldBe 7
        }
    }

    "status" should {
        "return SUCCESS if there are no failed items" {
            val result = ProcessingResult(totalCount = 10, failedCount = 0)

            result.status shouldBe ProcessingResultStatus.SUCCESS
        }

        "return FAILURE if all items failed" {
            val result = ProcessingResult(totalCount = 10, failedCount = 10)

            result.status shouldBe ProcessingResultStatus.FAILURE
        }

        "return PARTIAL_SUCCESS if some items failed" {
            val result = ProcessingResult(totalCount = 10, failedCount = 3)

            result.status shouldBe ProcessingResultStatus.PARTIAL_SUCCESS
        }

        "return PARTIAL_SUCCESS for a single failure" {
            val result = ProcessingResult(totalCount = 10, failedCount = 1)

            result.status shouldBe ProcessingResultStatus.PARTIAL_SUCCESS
        }

        "return PARTIAL_SUCCESS for almost all failures" {
            val result = ProcessingResult(totalCount = 10, failedCount = 9)

            result.status shouldBe ProcessingResultStatus.PARTIAL_SUCCESS
        }
    }
})
