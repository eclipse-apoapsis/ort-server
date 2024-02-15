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

package org.ossreviewtoolkit.server.utils.config

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class ExtensionsTest : WordSpec({
    "getInterpolatedString" should {
        "return a plain string if there are no variables" {
            val config = ConfigFactory.parseMap(mapOf("property" to "value"))

            config.getInterpolatedString("property", emptyMap()) shouldBe "value"
        }

        "substitute variables in the value" {
            val variables = mapOf("foo" to "fooValue", "bar" to "barValue")
            val config = ConfigFactory.parseMap(mapOf("property" to "\${foo}-\${bar}"))

            val value = config.getInterpolatedString("property", variables)

            value shouldBe "fooValue-barValue"
        }

        "not replace unknown placeholders" {
            val config = ConfigFactory.parseMap(mapOf("property" to "value \${foo}"))

            val value = config.getInterpolatedString("property", emptyMap())

            value shouldBe "value \${foo}"
        }
    }

    "getInterpolatedStringOrNull" should {
        "return null if the property is not contained in the configuration" {
            val config = ConfigFactory.empty()

            val value = config.getInterpolatedStringOrNull("property", mapOf("foo" to "bar"))

            value should beNull()
        }

        "return a value with substituted variables" {
            val variables = mapOf("foo" to "fooValue", "bar" to "barValue")
            val config = ConfigFactory.parseMap(mapOf("property" to "\${foo}-\${bar}"))

            val value = config.getInterpolatedStringOrNull("property", variables)

            value shouldBe "fooValue-barValue"
        }
    }

    "getInterpolatedStringOrDefault" should {
        "return the default value if the property is not contained in the configuration" {
            val config = ConfigFactory.empty()

            val value = config.getInterpolatedStringOrDefault("property", "default-\${foo}", mapOf("foo" to "bar"))

            value shouldBe "default-bar"
        }

        "return a value with substituted variables" {
            val variables = mapOf("foo" to "fooValue", "bar" to "barValue")
            val config = ConfigFactory.parseMap(mapOf("property" to "\${foo}-\${bar}"))

            val value = config.getInterpolatedStringOrDefault("property", "defaultValue", variables)

            value shouldBe "fooValue-barValue"
        }
    }
})
