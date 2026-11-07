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

package org.eclipse.apoapsis.ortserver.detekt

import dev.detekt.api.Config
import dev.detekt.api.modifiedText
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import dev.detekt.test.utils.compileContentForTest

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class SingleLineKDocTest : WordSpec({
    "SingleLineKDoc" should {
        "report a multi-line KDoc with a single line of content" {
            val findings = SingleLineKDoc(Config.empty).lint(
                """
                /**
                 * A class.
                 */
                class A
                """.trimIndent()
            )

            findings should haveSize(1)
        }

        "report a multi-line KDoc with blank lines around the content" {
            val findings = SingleLineKDoc(Config.empty).lint(
                """
                /**
                 *
                 * A class.
                 *
                 */
                class A
                """.trimIndent()
            )

            findings should haveSize(1)
        }

        "not report a single-line KDoc" {
            val findings = SingleLineKDoc(Config.empty).lint(
                """
                /** A class. */
                class A
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "not report a KDoc with multiple lines of content" {
            val findings = SingleLineKDoc(Config.empty).lint(
                """
                /**
                 * A class.
                 * With a second line.
                 */
                class A
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "not report a KDoc with a block tag" {
            val findings = SingleLineKDoc(Config.empty).lint(
                """
                /**
                 * @param value The value.
                 */
                class A(val value: Int)
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "not report a KDoc that would exceed the default maximum line length on a single line" {
            val findings = SingleLineKDoc(Config.empty).lint(
                """
                class A {
                    /**
                     * ${"x".repeat(113)}
                     */
                    fun f() = Unit
                }
                """.trimIndent()
            )

            findings should beEmpty()
        }

        "use the maximum line length configured for the MaxLineLength rule" {
            val rootConfig = TestConfig(
                "style" to mapOf("MaxLineLength" to mapOf("maxLineLength" to 20)),
                "OrtServer" to mapOf("SingleLineKDoc" to emptyMap<String, Any>())
            )
            val rule = SingleLineKDoc(rootConfig.subConfig("OrtServer").subConfig("SingleLineKDoc"))

            val findings = rule.lint(
                """
                /**
                 * Fits in 20.
                 */
                class A

                /**
                 * Does not fit in 20.
                 */
                class B
                """.trimIndent()
            )

            findings should haveSize(1)
            findings.single().entity.location.source.line shouldBe 1
        }

        "convert KDocs to a single line if auto-correct is enabled" {
            val file = compileContentForTest(
                """
                /**
                 * A class.
                 */
                class A {
                    /**
                     * A function.
                     */
                    fun f() = Unit

                    /**
                     * A function.
                     * With a second line.
                     */
                    fun g() = Unit
                }
                """.trimIndent()
            )

            SingleLineKDoc(TestConfig("autoCorrect" to true)).lint(file)

            file.modifiedText shouldBe """
                /** A class. */
                class A {
                    /** A function. */
                    fun f() = Unit

                    /**
                     * A function.
                     * With a second line.
                     */
                    fun g() = Unit
                }
            """.trimIndent()
        }

        "not modify the file if auto-correct is disabled" {
            val file = compileContentForTest(
                """
                /**
                 * A class.
                 */
                class A
                """.trimIndent()
            )

            SingleLineKDoc(Config.empty).lint(file)

            file.modifiedText should beNull()
        }
    }
})
