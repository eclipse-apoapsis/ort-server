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

package org.ossreviewtoolkit.server.workers.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class OptionsTransformerFactoryTest : StringSpec({
    "The transformation of all options should be possible" {
        val factory = OptionsTransformerFactory()

        val transformer = factory.newTransformer(testOptions)

        val transformedOptions = transformer.transform(::transform)

        transformedOptions.keys shouldContainExactlyInAnyOrder listOf("nexusIQ", "vulnerableCode")
        transformedOptions.getValue("nexusIQ") shouldBe mapOf(
            "username" to "${FILTER_PREFIX}userSecret$TRANSFORMED_SUFFIX",
            "password" to "${FILTER_PREFIX}passwordSecret$TRANSFORMED_SUFFIX",
            "url" to "https://nexus.example.org/api$TRANSFORMED_SUFFIX"
        )
        transformedOptions.getValue("vulnerableCode") shouldBe mapOf(
            "apiKey" to "${FILTER_PREFIX}vcKey$TRANSFORMED_SUFFIX",
            "url" to "https://vulnerablecode.example.org/api$TRANSFORMED_SUFFIX"
        )
    }

    "Filtering of options should be possible" {
        val factory = OptionsTransformerFactory()

        val transformer = factory.newTransformer(testOptions)

        val transformedOptions = transformer.filter { it.startsWith(FILTER_PREFIX) }
            .transform(::transform)

        transformedOptions.keys shouldContainExactlyInAnyOrder listOf("nexusIQ", "vulnerableCode")
        transformedOptions.getValue("nexusIQ") shouldBe mapOf(
            "username" to "${FILTER_PREFIX}userSecret$TRANSFORMED_SUFFIX",
            "password" to "${FILTER_PREFIX}passwordSecret$TRANSFORMED_SUFFIX",
            "url" to "https://nexus.example.org/api"
        )
        transformedOptions.getValue("vulnerableCode") shouldBe mapOf(
            "apiKey" to "${FILTER_PREFIX}vcKey$TRANSFORMED_SUFFIX",
            "url" to "https://vulnerablecode.example.org/api"
        )
    }

    "The transformation function should not be called for empty options" {
        val factory = OptionsTransformerFactory()

        val transformer = factory.newTransformer(testOptions)

        val transformedOptions = transformer.filter { false }
            .transform { throw IllegalStateException("Unexpected invocation.") }

        transformedOptions shouldBe testOptions
    }
})

/** A prefix used to mark specific properties that should be transformed. */
private const val FILTER_PREFIX = "toBeTransformed:"

/** A suffix appended by the transform function to mark a string as processed. */
private const val TRANSFORMED_SUFFIX = "_transformed"

/** A map with test options. */
private val testOptions = mapOf(
    "nexusIQ" to mapOf(
        "username" to "${FILTER_PREFIX}userSecret",
        "password" to "${FILTER_PREFIX}passwordSecret",
        "url" to "https://nexus.example.org/api"
    ),
    "vulnerableCode" to mapOf(
        "apiKey" to "${FILTER_PREFIX}vcKey",
        "url" to "https://vulnerablecode.example.org/api"
    )
)

/**
 * A simple transformation function on options.
 */
private fun transform(set: Set<String>): Map<String, String> =
    set.associateWith { s -> "$s$TRANSFORMED_SUFFIX" }
