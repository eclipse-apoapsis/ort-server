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

import io.kotest.assertions.AssertionErrorBuilder
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf

import io.mockk.every
import io.mockk.mockk

import kotlin.math.abs
import kotlin.time.Clock

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.Repository
import org.eclipse.apoapsis.ortserver.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

class ConfigValidatorTest : StringSpec({
    "A successful validation should be handled" {
        val script = loadScript("validation-success.params.kts")

        val run = mockRun()
        val context = mockContext(run)

        val validator = ConfigValidator.create(context)
        val validationResult = validator.validate(script).shouldBeTypeOf<ConfigValidationResultSuccess>()

        validationResult.resolvedConfigurations shouldBe run.jobConfigs

        val expectedIssue = Issue(
            Clock.System.now(),
            "validation",
            "Current repository is ${testHierarchy.repository.url}",
            Severity.HINT
        )
        validationResult.issues.shouldBeSingleton {
            checkIssue(expectedIssue, it)
        }

        validationResult.labels.entries.shouldBeSingleton { (key, value) ->
            key shouldBe "test"
            value shouldBe "success"
        }
    }

    "A failed validation should be handled" {
        val script = loadScript("validation-failure.params.kts")
        val context = mockContext(mockk())

        val validator = ConfigValidator.create(context)
        val validationResult = validator.validate(script).shouldBeTypeOf<ConfigValidationResultFailure>()

        val expectedIssue = Issue(
            Clock.System.now(),
            "validation",
            "Current repository is ${testHierarchy.repository.url}; invalid parameters.",
            Severity.ERROR
        )
        validationResult.issues.shouldBeSingleton {
            checkIssue(expectedIssue, it)
        }
    }

    "An invalid script should be handled" {
        val script = "This is not a valid Kotlin script!"
        val context = mockContext(mockk())

        val validator = ConfigValidator.create(context)
        val validationResult = validator.validate(script).shouldBeTypeOf<ConfigValidationResultFailure>()

        validationResult.issues.shouldBeSingleton {
            it.source shouldBe INVALID_SCRIPT_SOURCE
            it.message shouldContain "executing validation script"
            it.severity shouldBe Severity.ERROR
        }
    }
})

/** The name of the rule set used by the test cases. */
private const val RULE_SET = "testRuleSet"

/** The resolved configuration context used by the test cases. */
private const val RESOLVED_CONTEXT = "testResolvedContext"

/** Test organization ID. */
private const val ORGANIZATION_ID = 1L

/** A hierarchy used by the test cases. */
val testHierarchy = Hierarchy(
    repository = Repository(20230801094107L, ORGANIZATION_ID, 2, RepositoryType.GIT, "https://repo.example.org"),
    organization = mockk(),
    product = mockk()
)

/**
 * Load the script with the given [name] from resources and return it as a string.
 */
private fun loadScript(name: String): String =
    ConfigValidatorTest::class.java.getResource("/$name")?.readText()
        ?: AssertionErrorBuilder.fail("Could not load script file '$name'.")

/**
 * Check whether the given [issue][actual] corresponds to the given [expected] issue. This function does not do an
 * exact match on the timestamp, but accepts a certain deviation.
 */
private fun checkIssue(expected: Issue, actual: Issue) {
    actual.copy(timestamp = expected.timestamp) shouldBe expected
    val deltaT = actual.timestamp - expected.timestamp
    abs(deltaT.inWholeMilliseconds) shouldBeLessThan 30000
}

/**
 * Return a mock for a [WorkerContext] that is prepared to return the given [run], [configMan], and the
 * [testHierarchy].
 */
private fun mockContext(run: OrtRun, configMan: ConfigManager = mockConfigManager()) =
    mockk<WorkerContext> {
        every { ortRun } returns run
        every { hierarchy } returns testHierarchy
        every { configManager } returns configMan
    }

/**
 * Return a mock for a [ConfigManager] that is prepared to answer queries about the existence of config files.
 */
private fun mockConfigManager(): ConfigManager =
    mockk {
        every { containsFile(any(), any()) } returns true
    }

/**
 * Return a mock [OrtRun] that is prepared to give some default answers. It provides a job configuration with the
 * given [ruleSetName].
 */
private fun mockRun(ruleSetName: String = RULE_SET): OrtRun {
    val fixtures = Fixtures(mockk())
    val configs = fixtures.jobConfigurations.copy(ruleSet = ruleSetName)

    return mockk {
        every { resolvedJobConfigContext } returns RESOLVED_CONTEXT
        every { jobConfigs } returns configs
        every { resolvedJobConfigs } returns jobConfigs
        every { organizationId } returns ORGANIZATION_ID
    }
}
