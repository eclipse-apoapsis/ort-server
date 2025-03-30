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
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify

import kotlinx.datetime.Clock

import org.eclipse.apoapsis.ortserver.config.ConfigException
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.dao.test.mockkTransaction
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.OrtRunStatus
import org.eclipse.apoapsis.ortserver.model.Severity
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.runs.Issue
import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.model.util.asPresent
import org.eclipse.apoapsis.ortserver.workers.common.RunResult
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContextFactory
import org.eclipse.apoapsis.ortserver.workers.common.resolvedConfigurationContext

class ConfigWorkerTest : StringSpec({
    beforeSpec {
        mockkObject(ConfigValidator)
    }

    afterSpec {
        unmockkAll()
    }

    "The ORT run configuration should be validated successfully" {
        val (contextFactory, _) = mockContext()
        val configManager = mockConfigManager()

        val resolvedConfig = mockk<JobConfigurations>()
        mockValidator(ConfigValidationResultSuccess(resolvedConfig, validationIssues, validationLabels))

        val ortRunRepository = mockk<OrtRunRepository> {
            every {
                update(RUN_ID, any(), any(), any(), any(), any(), any(), any(), any())
            } returns mockk()
        }

        mockkTransaction {
            val worker = ConfigWorker(mockk(), ortRunRepository, contextFactory, configManager)
            worker.testRun() shouldBe RunResult.Success

            verify {
                configManager.resolveContext(Context(ORIGINAL_CONTEXT))

                ortRunRepository.update(
                    id = RUN_ID,
                    resolvedJobConfigs = resolvedConfig.asPresent(),
                    resolvedJobConfigContext = RESOLVED_CONTEXT.asPresent(),
                    issues = validationIssues.asPresent(),
                    labels = validationLabels.asPresent()
                )
            }
        }
    }

    "A null configuration context should be used if the user has not specified one" {
        val (contextFactory, _) = mockContext(null)
        val configManager = mockConfigManager()

        val resolvedConfig = mockk<JobConfigurations>()
        mockValidator(ConfigValidationResultSuccess(resolvedConfig, validationIssues))

        val ortRunRepository = mockk<OrtRunRepository> {
            every {
                update(RUN_ID, any(), any(), any(), any(), any(), any(), any())
            } returns mockk()
        }

        mockkTransaction {
            val worker = ConfigWorker(mockk(), ortRunRepository, contextFactory, configManager)
            worker.testRun() shouldBe RunResult.Success

            verify {
                configManager.resolveContext(null)

                ortRunRepository.update(
                    id = RUN_ID,
                    resolvedJobConfigs = resolvedConfig.asPresent(),
                    resolvedJobConfigContext = RESOLVED_CONTEXT.asPresent(),
                    issues = validationIssues.asPresent(),
                    labels = OptionalValue.Absent
                )
            }
        }
    }

    "A failed validation should be handled" {
        val (contextFactory, _) = mockContext()
        val configManager = mockConfigManager()

        mockValidator(ConfigValidationResultFailure(validationIssues))

        val ortRunRepository = mockk<OrtRunRepository> {
            every {
                update(RUN_ID, any(), any(), any(), any(), any(), any(), any())
            } returns mockk()
        }

        mockkTransaction {
            val worker = ConfigWorker(mockk(), ortRunRepository, contextFactory, configManager)
            when (val result = worker.testRun()) {
                is RunResult.Failed -> result.error should beInstanceOf<IllegalArgumentException>()
                else -> fail("Unexpected result: $result")
            }

            verify {
                ortRunRepository.update(
                    id = RUN_ID,
                    resolvedJobConfigContext = RESOLVED_CONTEXT.asPresent(),
                    issues = validationIssues.asPresent()
                )
            }
        }
    }

    "Exceptions during validation should be handled" {
        val (contextFactory, _) = mockContext()

        val configException = ConfigException("Test exception", null)
        val configManager = mockConfigManager()
        every { configManager.getFileAsString(any(), any()) } throws configException

        val worker = ConfigWorker(mockk(), mockk(), contextFactory, configManager)
        when (val result = worker.testRun()) {
            is RunResult.Failed -> result.error shouldBe configException
            else -> fail("Unexpected result: $result")
        }
    }

    "The context passed to the validator should have the correct resolved configuration context" {
        val (contextFactory, context) = mockContext()
        val configManager = mockConfigManager()

        val resolvedConfig = mockk<JobConfigurations>()
        mockValidator(ConfigValidationResultSuccess(resolvedConfig, validationIssues))

        val ortRunRepository = mockk<OrtRunRepository> {
            every {
                update(RUN_ID, any(), any(), any(), any(), any(), any(), any())
            } returns mockk()
        }

        mockkTransaction {
            val worker = ConfigWorker(mockk(), ortRunRepository, contextFactory, configManager)
            worker.testRun() shouldBe RunResult.Success

            val slotContext = mutableListOf<WorkerContext>()
            verify {
                ConfigValidator.create(capture(slotContext))
            }
            val capturedContext = slotContext.last()

            // Test whether normal delegation works with the context.
            val testHierarchy = mockk<Hierarchy>()
            every { context.hierarchy } returns testHierarchy
            capturedContext.hierarchy shouldBe testHierarchy

            // Test whether the resolved configuration context has been overloaded.
            capturedContext.resolvedConfigurationContext shouldBe Context(RESOLVED_CONTEXT)
        }
    }

    "A missing validation script should be ignored" {
        val (contextFactory, context) = mockContext()
        val configManager = mockConfigManager(validationScriptExists = false)

        val resolvedConfig = mockk<JobConfigurations>()
        mockValidator(ConfigValidationResultSuccess(resolvedConfig, validationIssues, validationLabels))

        val ortRunRepository = mockk<OrtRunRepository> {
            every {
                update(RUN_ID, any(), any(), any(), any(), any(), any())
            } returns mockk()
        }

        mockkTransaction {
            val worker = ConfigWorker(mockk(), ortRunRepository, contextFactory, configManager)
            worker.testRun() shouldBe RunResult.Success

            val expectedJobConfigs = context.ortRun.jobConfigs

            verify {
                configManager.resolveContext(Context(ORIGINAL_CONTEXT))

                ortRunRepository.update(
                    id = RUN_ID,
                    resolvedJobConfigs = expectedJobConfigs.asPresent(),
                    resolvedJobConfigContext = RESOLVED_CONTEXT.asPresent()
                )
            }
        }
    }
})

/** The ID of a test run. */
private const val RUN_ID = 20230802103508L

/** An original context that needs to be resolved. */
private const val ORIGINAL_CONTEXT = "theContextAsPassedByTheUser"

/** A resolved context. */
private const val RESOLVED_CONTEXT = "theResolvedConfigurationContext"

/** Simulated script to perform parameter validation and transformation. */
private const val PARAMETERS_SCRIPT = "Script to validate parameters"

/** A list with validation issues to be returned by the mock validator. */
private val validationIssues = listOf(
    Issue(Clock.System.now(), "ConfigWorkerTest", "Test message", Severity.ERROR)
)

/** A map with labels to be returned by the mock validator. */
private val validationLabels = mapOf("validated" to "yes", "test" to "true")

/**
 * Create a mock context factory together with a mock context that is returned by the factory. Prepare the mocks to
 * return typical answers. Use the given [orgConfigContext] for the [OrtRun.jobConfigContext] property.
 */
private fun mockContext(orgConfigContext: String? = ORIGINAL_CONTEXT): Pair<WorkerContextFactory, WorkerContext> {
    val run = OrtRun(
        id = 1,
        index = 2,
        organizationId = 3,
        productId = 4,
        repositoryId = 5,
        revision = "main",
        path = null,
        createdAt = Clock.System.now(),
        jobConfigs = JobConfigurations(),
        resolvedJobConfigs = null,
        status = OrtRunStatus.ACTIVE,
        finishedAt = null,
        labels = emptyMap(),
        vcsId = null,
        vcsProcessedId = null,
        nestedRepositoryIds = null,
        repositoryConfigId = null,
        issues = emptyList(),
        jobConfigContext = orgConfigContext,
        resolvedJobConfigContext = null,
        traceId = "trace-id"
    )

    val context = mockk<WorkerContext> {
        every { ortRun } returns run
    }

    val slot = slot<suspend (WorkerContext) -> RunResult>()
    val factory = mockk<WorkerContextFactory> {
        coEvery { withContext(RUN_ID, capture(slot)) } coAnswers {
            slot.captured(context)
        }
    }

    return factory to context
}

/**
 * Create a mock [ConfigValidator] and prepare the factory function to return it. The mock is configured to return the
 * given [result] for the test script.
 */
private fun mockValidator(result: ConfigValidationResult): ConfigValidator {
    val validator = mockk<ConfigValidator> {
        every { validate(PARAMETERS_SCRIPT) } returns result
    }

    every { ConfigValidator.create(any()) } returns validator

    return validator
}

/**
 * Create a mock [ConfigManager]. The mock is configured to return the test validation script.
 */
private fun mockConfigManager(validationScriptExists: Boolean = true): ConfigManager = mockk<ConfigManager> {
    every {
        containsFile(Context(RESOLVED_CONTEXT), ConfigWorker.VALIDATION_SCRIPT_PATH)
    } returns validationScriptExists

    every {
        getFileAsString(Context(RESOLVED_CONTEXT), ConfigWorker.VALIDATION_SCRIPT_PATH)
    } returns PARAMETERS_SCRIPT

    every { resolveContext(any()) } returns Context(RESOLVED_CONTEXT)
}

/**
 * Helper function to invoke the test worker with test parameters in a coroutine context.
 */
private suspend fun ConfigWorker.testRun(): RunResult = run(RUN_ID)
