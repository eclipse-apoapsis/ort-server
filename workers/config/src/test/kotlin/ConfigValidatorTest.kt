/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.config

import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf

import io.mockk.every
import io.mockk.mockk

import kotlin.math.abs

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.runs.OrtIssue
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

class ConfigValidatorTest : StringSpec({
    "A successful validation should be handled" {
        val fixtures = Fixtures(mockk())
        val script = loadScript("validation-success.params.kts")

        val run = mockk<OrtRun> {
            every { jobConfigs } returns fixtures.jobConfigurations
        }
        val context = mockContext(run)

        val validator = ConfigValidator.create(context)
        val validationResult = validator.validate(script).shouldBeTypeOf<ConfigValidationResultSuccess>()

        validationResult.resolvedConfigurations shouldBe run.jobConfigs

        val expectedIssue = OrtIssue(
            Clock.System.now(),
            "validation",
            "Current repository is ${testHierarchy.repository.url}",
            "Hint"
        )
        validationResult.issues shouldHaveSize 1
        checkIssue(expectedIssue, validationResult.issues[0])

        validationResult.labels shouldHaveSize 1
        validationResult.labels["test"] shouldBe "success"
    }

    "A failed validation should be handled" {
        val script = loadScript("validation-failure.params.kts")
        val context = mockContext(mockk())

        val validator = ConfigValidator.create(context)
        val validationResult = validator.validate(script).shouldBeTypeOf<ConfigValidationResultFailure>()

        val expectedIssue = OrtIssue(
            Clock.System.now(),
            "validation",
            "Current repository is ${testHierarchy.repository.url}; invalid parameters.",
            "Error"
        )
        validationResult.issues shouldHaveSize 1
        checkIssue(expectedIssue, validationResult.issues[0])
    }

    "An invalid script should be handled" {
        val script = "This is not a valid Kotlin script!"
        val context = mockContext(mockk())

        val validator = ConfigValidator.create(context)
        val validationResult = validator.validate(script).shouldBeTypeOf<ConfigValidationResultFailure>()

        validationResult.issues shouldHaveSize 1
        with(validationResult.issues[0]) {
            source shouldBe ConfigValidator.INVALID_SCRIPT_SOURCE
            message shouldContain "executing validation script"
            severity shouldBe "ERROR"
        }
    }
})

/** A hierarchy used by the test cases. */
val testHierarchy = Hierarchy(
    repository = Repository(20230801094107L, 1, 2, RepositoryType.GIT, "https://repo.example.org"),
    organization = mockk(),
    product = mockk()
)

/**
 * Load the script with the given [name] from resources and return it as a string.
 */
private fun loadScript(name: String): String =
    ConfigValidatorTest::class.java.getResource("/$name")?.readText() ?: fail("Could not load script file '$name'.")

/**
 * Check whether the given [issue][actual] corresponds to the given [expected] issue. This function does not do an
 * exact match on the timestamp, but accepts a certain deviation.
 */
private fun checkIssue(expected: OrtIssue, actual: OrtIssue) {
    actual.copy(timestamp = expected.timestamp) shouldBe expected
    val deltaT = actual.timestamp - expected.timestamp
    abs(deltaT.inWholeMilliseconds) shouldBeLessThan 30000
}

/**
 * Return a mock for a [WorkerContext] that is prepared to return the given [run] and the [testHierarchy].
 */
private fun mockContext(run: OrtRun) =
    mockk<WorkerContext> {
        every { ortRun } returns run
        every { hierarchy } returns testHierarchy
    }
