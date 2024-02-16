/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.transport.json

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import kotlinx.serialization.Serializable

class JsonSerializerTest : StringSpec({
    "Serialization should work" {
        val data = TestStringData("some test data")
        val serializer = JsonSerializer.forType<TestData>()

        val json = serializer.toJson(data)
        val data2 = serializer.fromJson(json)

        data2 shouldBe data
    }

    "Serialization should work over all sealed classes" {
        val data = TestIntData(42)
        val serializer = JsonSerializer.forType<TestData>()

        val json = serializer.toJson(data)
        val data2 = serializer.fromJson(json)

        data2 shouldBe data
    }
})

@Serializable
internal sealed class TestData

@Serializable
internal data class TestStringData(val data: String) : TestData()

@Serializable
internal data class TestIntData(val data: Int) : TestData()
