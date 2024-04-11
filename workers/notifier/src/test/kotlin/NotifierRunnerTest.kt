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

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify

import java.io.File

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Context
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.MailNotificationConfiguration
import org.eclipse.apoapsis.ortserver.model.MailServerConfiguration
import org.eclipse.apoapsis.ortserver.model.NotifierJobConfiguration
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.notifier.Notifier

const val NOTIFICATION_SET = "default"
private val resolvedConfigContext = Context("resolvedContext")
private val script = File("src/test/resources/example.notifications.kts")

class NotifierRunnerTest : WordSpec({
    val runner = NotifierRunner()

    lateinit var notifierConfig: NotifierJobConfiguration

    beforeSpec {
        notifierConfig = NotifierJobConfiguration(
            notifierRules = "default",
            mail = MailNotificationConfiguration(
                recipientAddresses = listOf("test@example.com"),
                mailServerConfiguration = MailServerConfiguration(
                    hostName = "localhost",
                    port = 465,
                    username = "secret-username",
                    password = "secret-password",
                    useSsl = false,
                    fromAddress = "no-reply@oss-review-toolkit.org"
                )
            )
        )
    }

    afterEach { unmockkAll() }

    "run" should {
        "invoke the ORT notifier" {
            mockkConstructor(Notifier::class)

            every { anyConstructed<Notifier>().run(any()) } returns mockk()

            runner.run(
                ortResult = OrtResult.EMPTY,
                config = notifierConfig,
                workerContext = createWorkerContext()
            )

            verify(exactly = 1) {
                anyConstructed<Notifier>().run(script.readText())
            }
        }
    }
})

private fun createWorkerContext(): WorkerContext {
    val configManagerMock = createConfigManager()

    return mockk {
        every { configManager } returns configManagerMock
        every { ortRun.resolvedJobConfigContext } returns resolvedConfigContext.name
    }
}

private fun createConfigManager() = mockk<ConfigManager> {
    every { getFileAsString(resolvedConfigContext, Path(NOTIFICATION_SET)) } returns
            File("src/test/resources/example.notifications.kts").readText()

    every { getSecret(Path("secret-username")) } returns "no-reply@oss-review-toolkit.org"
    every { getSecret(Path("secret-password")) } returns "hunter2"

    every { getFile(resolvedConfigContext, Path("resolutions.yml")) } returns
            File("src/test/resources/resolutions.yml").inputStream()
}
