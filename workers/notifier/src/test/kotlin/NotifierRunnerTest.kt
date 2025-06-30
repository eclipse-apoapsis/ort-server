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

package org.eclipse.apoapsis.ortserver.workers.notifier

import io.kotest.core.spec.style.WordSpec

import io.mockk.EqMatcher
import io.mockk.OfTypeMatcher
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify

import java.io.File

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.services.config.AdminConfig
import org.eclipse.apoapsis.ortserver.services.config.AdminConfigService
import org.eclipse.apoapsis.ortserver.services.config.JiraRestClientConfiguration
import org.eclipse.apoapsis.ortserver.services.config.MailServerConfiguration
import org.eclipse.apoapsis.ortserver.services.config.NotifierConfig
import org.eclipse.apoapsis.ortserver.services.config.RuleSet
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.JiraConfiguration
import org.ossreviewtoolkit.model.config.NotifierConfiguration as OrtNotifierConfiguration
import org.ossreviewtoolkit.model.config.SendMailConfiguration
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.notifier.Notifier

private const val NOTIFICATION_SET = "default"
private const val RULE_SET = "testRuleSet"
private const val RESOLUTION_FILE = "resolutions.yml"
private const val ORGANIZATION_ID = 28L
private val resolvedConfigContext = Context("resolvedContext")
private val script = File("src/test/resources/example.notifications.kts")

class NotifierRunnerTest : WordSpec({
    beforeEach {
        mockkConstructor(Notifier::class)
    }

    afterEach { unmockkAll() }

    "run" should {
        "invoke the ORT notifier" {
            val ortNotifierConfig = OrtNotifierConfiguration(
                mail = SendMailConfiguration(
                    hostName = "localhost",
                    port = 465,
                    username = "no-reply@oss-review-toolkit.org",
                    password = "hunter2",
                    useSsl = false,
                    fromAddress = "no-reply@oss-review-toolkit.org"
                ),
                jira = JiraConfiguration(
                    host = "https://jira.example.com",
                    username = "jiraUser",
                    password = "jiraPass"
                )
            )

            every {
                constructedWith<Notifier>(
                    EqMatcher(ortResult),
                    EqMatcher(ortNotifierConfig),
                    OfTypeMatcher<DefaultResolutionProvider>(DefaultResolutionProvider::class)
                ).run(any())
            } returns mockk()

            val runner = NotifierRunner(createAdminConfigService())
            runner.run(
                ortResult = ortResult,
                jobConfigurations = JobConfigurations(ruleSet = RULE_SET),
                workerContext = createWorkerContext()
            )

            verify(exactly = 1) {
                constructedWith<Notifier>(
                    EqMatcher(ortResult),
                    EqMatcher(ortNotifierConfig),
                    OfTypeMatcher<DefaultResolutionProvider>(DefaultResolutionProvider::class)
                ).run(script.readText())
            }
        }

        "evaluate the disable flags in the admin configuration" {
            val disabledNotifierConfig = notifierConfig.copy(
                disableMailNotifications = true,
                disableJiraNotifications = true
            )
            val disabledAdminConfig = AdminConfig(notifierConfig = disabledNotifierConfig)

            val ortNotifierConfig = OrtNotifierConfiguration()

            every {
                constructedWith<Notifier>(
                    EqMatcher(ortResult),
                    EqMatcher(ortNotifierConfig),
                    OfTypeMatcher<DefaultResolutionProvider>(DefaultResolutionProvider::class)
                ).run(any())
            } returns mockk()

            val runner = NotifierRunner(createAdminConfigService(disabledAdminConfig))
            runner.run(
                ortResult = ortResult,
                jobConfigurations = JobConfigurations(),
                workerContext = createWorkerContext()
            )

            verify(exactly = 1) {
                constructedWith<Notifier>(
                    EqMatcher(ortResult),
                    EqMatcher(ortNotifierConfig),
                    OfTypeMatcher<DefaultResolutionProvider>(DefaultResolutionProvider::class)
                ).run(any())
            }
        }
    }
})

/** A test ORT result. */
private val ortResult = OrtResult.EMPTY.copy(
    repository = Repository.EMPTY.copy(vcs = VcsInfo(VcsType.GIT, "https://example.com/repo.git", "main")),
    labels = mapOf("foo" to "bar")
)

/** The default configuration for the notifier in the admin configuration. */
private val notifierConfig = NotifierConfig(
    mail = MailServerConfiguration(
        hostName = "localhost",
        port = 465,
        username = "secret-username",
        password = "secret-password",
        useSsl = false,
        fromAddress = "no-reply@oss-review-toolkit.org"
    ),
    jira = JiraRestClientConfiguration(
        serverUrl = "https://jira.example.com",
        username = "jira-secret-username",
        password = "jira-secret-password"
    ),
    notifierRules = "default"
)

/** The configuration returned by default by the mock [AdminConfigService]. */
private val adminConfig = AdminConfig(
    notifierConfig = notifierConfig,
    ruleSets = mapOf(
        RULE_SET to RuleSet(
            copyrightGarbageFile = "copyright-garbage.txt",
            licenseClassificationsFile = "license-classifications.yml",
            resolutionsFile = RESOLUTION_FILE,
            evaluatorRules = "evaluator.rules.kts"
        )
    )
)

private fun createWorkerContext(): WorkerContext {
    val configManagerMock = createConfigManager()

    return mockk {
        every { configManager } returns configManagerMock
        every { ortRun.resolvedJobConfigContext } returns resolvedConfigContext.name
        every { ortRun.organizationId } returns ORGANIZATION_ID
    }
}

private fun createConfigManager() = mockk<ConfigManager> {
    every { getFileAsString(resolvedConfigContext, Path(NOTIFICATION_SET)) } returns
            File("src/test/resources/example.notifications.kts").readText()

    every { getSecret(Path("secret-username")) } returns "no-reply@oss-review-toolkit.org"
    every { getSecret(Path("secret-password")) } returns "hunter2"
    every { getSecret(Path("jira-secret-username")) } returns "jiraUser"
    every { getSecret(Path("jira-secret-password")) } returns "jiraPass"

    every { getFile(resolvedConfigContext, Path(RESOLUTION_FILE)) } returns
            File("src/test/resources/resolutions.yml").inputStream()
}

/**
 * Create a mock [AdminConfigService] and prepare it to return the given [config].
 */
private fun createAdminConfigService(config: AdminConfig = adminConfig): AdminConfigService =
    mockk {
        every { loadAdminConfig(resolvedConfigContext, ORGANIZATION_ID) } returns config
    }
