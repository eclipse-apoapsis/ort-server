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

package org.eclipse.apoapsis.ortserver.utils.config

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.beEmpty as beEmptyMap
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

    "getObjectOrDefault" should {
        "return the properties of a defined object" {
            val config = ConfigFactory.parseMap(
                mapOf(
                    "object" to mapOf("name" to "Ted", "age" to 42)
                )
            )

            val result = config.getObjectOrDefault("object") { throw IllegalStateException("Unexpected call.") }

            result.keys shouldHaveSize 2
            result["name"]?.unwrapped() shouldBe "Ted"
            result["age"]?.unwrapped() shouldBe 42
        }

        "return the result of the default function for an undefined object" {
            val config = ConfigFactory.empty()
            val defaultMap = mapOf(
                "name" to ConfigValueFactory.fromAnyRef("Ted"),
                "age" to ConfigValueFactory.fromAnyRef(42)
            )

            val result = config.getObjectOrDefault("undefinedObject") { defaultMap }

            result shouldBe defaultMap
        }
    }

    "getObjectOrEmpty" should {
        "return the properties of a defined object" {
            val config = ConfigFactory.parseMap(
                mapOf(
                    "object" to mapOf("name" to "Ted", "age" to 42)
                )
            )

            val result = config.getObjectOrEmpty("object")

            result.keys shouldHaveSize 2
            result["name"]?.unwrapped() shouldBe "Ted"
            result["age"]?.unwrapped() shouldBe 42
        }

        "return an empty map for an undefined object" {
            val config = ConfigFactory.empty()

            val result = config.getObjectOrEmpty("undefinedObject")

            result should beEmptyMap()
        }
    }

    "getStringListOrDefault" should {
        "return a list of strings for a defined property" {
            val config = ConfigFactory.parseMap(
                mapOf("list" to listOf("one", "two", "three"))
            )

            val result = config.getStringListOrDefault("list") { throw IllegalStateException("Unexpected call.") }

            result should containExactly("one", "two", "three")
        }

        "return the result of the default function for an undefined property" {
            val defaultList = listOf("defaultOne", "defaultTwo")
            val config = ConfigFactory.empty()

            val result = config.getStringListOrDefault("undefinedList") { defaultList }

            result shouldBe defaultList
        }
    }

    "getStringListOrEmpty" should {
        "return a list of strings for a defined property" {
            val config = ConfigFactory.parseMap(
                mapOf("list" to listOf("one", "two", "three"))
            )

            val result = config.getStringListOrEmpty("list")

            result should containExactly("one", "two", "three")
        }

        "return an empty list for an undefined property" {
            val config = ConfigFactory.empty()

            val result = config.getStringListOrEmpty("undefinedList")

            result should beEmpty()
        }
    }
})
