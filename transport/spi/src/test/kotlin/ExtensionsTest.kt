/*
 * Copyright (C) 2024 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class ExtensionsTest : WordSpec({
    "selectByPrefix" should {
        "filter the keys of a map based on their prefix" {
            val prefix = "foo"
            val inputMap = mapOf(
                "$prefix.p1" to "v1",
                "$prefix.p2" to "v2",
                "bar.p1" to "barVal"
            )
            val expectedOutputMap = mapOf(
                "p1" to "v1",
                "p2" to "v2"
            )

            inputMap.selectByPrefix(prefix) shouldBe expectedOutputMap
        }

        "handle a prefix ending on a separator character" {
            val prefix = "foo."
            val inputMap = mapOf(
                "${prefix}p1" to "v1",
                "${prefix}p2" to "v2",
                "bar.p1" to "barVal"
            )
            val expectedOutputMap = mapOf(
                "p1" to "v1",
                "p2" to "v2"
            )

            inputMap.selectByPrefix(prefix) shouldBe expectedOutputMap
        }
    }
})
