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

package org.eclipse.apoapsis.ortserver.workers.advisor

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify

import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.services.ortrun.mapToOrt
import org.eclipse.apoapsis.ortserver.workers.common.context.WorkerContext

import org.ossreviewtoolkit.advisor.AdviceProvider
import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

class AdvisorRunnerTest : WordSpec({
    val runner = AdvisorRunner()

    afterEach { unmockkAll() }

    "run" should {
        "return an OrtResult with a valid AdvisorRun" {
            val factory = mockAdviceProviderFactory("VulnerableCode")
            mockAdvisorAll(listOf(factory))

            val result = runner.run(
                mockContext(),
                OrtResult.EMPTY.copy(analyzer = AnalyzerRun.EMPTY),
                AdvisorJobConfiguration()
            )

            val run = result.advisor.shouldNotBeNull()
            run.config shouldBe AdvisorConfiguration(config = emptyMap())
        }

        "create the configured advice providers with the correct options and secrets" {
            val osvFactory = mockAdviceProviderFactory("OSV")
            val vulnerableCodeFactory = mockAdviceProviderFactory("VulnerableCode")
            mockAdvisorAll(listOf(osvFactory, vulnerableCodeFactory))

            val osvSecretRefs = mapOf("secret1" to "passRef1", "secret2" to "passRef2")
            val osvSecrets = mapOf("secret1" to "pass1", "secret2" to "pass2")
            val osvConfig = PluginConfig(
                options = mapOf("option1" to "value1", "option2" to "value2"),
                secrets = osvSecretRefs
            )

            val vulnerableCodeSecretRefs = mapOf("secret3" to "passRef3", "secret4" to "passRef4")
            val vulnerableCodeSecrets = mapOf("secret3" to "pass3", "secret4" to "pass4")
            val vulnerableCodeConfig = PluginConfig(
                options = mapOf("option3" to "value3", "option4" to "value4"),
                secrets = vulnerableCodeSecretRefs
            )

            val jobConfig = AdvisorJobConfiguration(
                advisors = listOf("OSV", "VulnerableCode"),
                config = mapOf(
                    "OSV" to osvConfig,
                    "VulnerableCode" to vulnerableCodeConfig
                )
            )

            val resolvedPluginConfig = mapOf(
                "OSV" to osvConfig.copy(secrets = osvSecrets),
                "VulnerableCode" to vulnerableCodeConfig.copy(secrets = vulnerableCodeSecrets)
            )
            val context = mockContext(jobConfig, resolvedPluginConfig)

            runner.run(
                context,
                OrtResult.EMPTY.copy(
                    analyzer = AnalyzerRun.EMPTY.copy(
                        result = AnalyzerResult.EMPTY.copy(
                            packages = setOf(Package.EMPTY)
                        )
                    )
                ),
                jobConfig
            )

            verify(exactly = 1) {
                osvFactory.create(osvConfig.copy(secrets = osvSecrets).mapToOrt())
                vulnerableCodeFactory.create(vulnerableCodeConfig.copy(secrets = vulnerableCodeSecrets).mapToOrt())
            }
        }
    }
})

private fun mockAdviceProviderFactory(adviceProviderName: String) =
    mockk<AdviceProviderFactory> {
        val pluginDescriptor =
            PluginDescriptor(id = adviceProviderName, displayName = adviceProviderName, description = "")

        every { descriptor } returns pluginDescriptor

        every { create(any()) } returns mockk<AdviceProvider> {
            every { descriptor } returns pluginDescriptor
            coEvery { retrievePackageFindings(any()) } returns emptyMap()
        }
    }

private fun mockAdvisorAll(adviceProviders: List<AdviceProviderFactory>) {
    mockkObject(AdviceProviderFactory)
    every { AdviceProviderFactory.ALL } returns adviceProviders.associateByTo(sortedMapOf()) { it.descriptor.id }
}

/**
 * Create a mock for the [WorkerContext] and prepare it to return the given [resolvedPluginConfig] when called to
 * resolve the secrets in the plugin configuration of the given [jobConfig].
 */
private fun mockContext(
    jobConfig: AdvisorJobConfiguration = AdvisorJobConfiguration(),
    resolvedPluginConfig: Map<String, PluginConfig> = emptyMap()
): WorkerContext =
    mockk {
        coEvery { resolvePluginConfigSecrets(jobConfig.config) } returns resolvedPluginConfig
    }
