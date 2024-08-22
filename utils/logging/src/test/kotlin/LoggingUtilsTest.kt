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

package org.eclipse.apoapsis.ortserver.utils.logging

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext

import org.slf4j.MDC

class LoggingUtilsTest : WordSpec({
    "withMdcContext" should {
        "add the provided elements to the MDC context" {
            withMdcContext("key1" to "val1", "key2" to "val2") {
                MDC.get("key1") shouldBe "val1"
                MDC.get("key2") shouldBe "val2"
            }
        }

        "overwrite existing elements in the MDC context" {
            MDC.setContextMap(mapOf("key1" to "val1", "key2" to "val2"))
            MDC.get("key1") shouldBe "val1"
            // We have to add the MDCContext to the coroutine context, otherwise it would be lost if this test function
            // is suspended during execution.
            withContext(MDCContext()) {
                withMdcContext("key1" to "new1", "key2" to "new2") {
                    MDC.get("key1") shouldBe "new1"
                    MDC.get("key2") shouldBe "new2"
                }
            }
        }

        "restore the previous MDC context after executing the block" {
            MDC.setContextMap(mapOf("key1" to "val1", "key2" to "val2"))
            // We have to add the MDCContext to the coroutine context, otherwise it would be lost if this test function
            // is suspended during execution.
            withContext(MDCContext()) {
                withMdcContext("key1" to "new1", "key2" to "new2") {}
                MDC.get("key1") shouldBe "val1"
                MDC.get("key2") shouldBe "val2"
            }
        }
    }
})
