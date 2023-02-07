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

package org.ossreviewtoolkit.server.workers.advisor

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import io.mockk.EqMatcher
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject

import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.advisor.Advisor
import org.ossreviewtoolkit.model.AdvisorRun
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.NexusIqConfiguration
import org.ossreviewtoolkit.model.config.OsvConfiguration
import org.ossreviewtoolkit.model.config.VulnerableCodeConfiguration

class AdvisorConfiguratorTest : StringSpec() {
    init {
        beforeTest {
            mockkConstructor(Advisor::class)
            mockkObject(Advisor)
        }

        afterTest {
            unmockkObject(Advisor)
            unmockkConstructor(Advisor::class)
        }

        "A default OSV AdvisorProvider should be configured" {
            val appConfig = ConfigFactory.empty()
            val providers = listOf("OSV")
            val advisorConfig = AdvisorConfiguration(osv = OsvConfiguration("https://api.osv.dev"))

            testAdvisorCreation(
                existingProviders = providers,
                selectedProviders = providers,
                expectedProviders = providers,
                appConfig = appConfig,
                expectedAdvisorConfig = advisorConfig
            )
        }

        "An OSV AdvisorProvider with a custom URL should be configured" {
            val serverUrl = "https://osv.exampl.org/custom"
            val osvConfig = mapOf("serverUrl" to serverUrl)

            val appConfig = createConfigForProviders("OSV" to osvConfig)
            val providers = listOf("OSV")
            val advisorConfig = AdvisorConfiguration(osv = OsvConfiguration(serverUrl))

            testAdvisorCreation(
                existingProviders = providers,
                selectedProviders = providers,
                expectedProviders = providers,
                appConfig = appConfig,
                expectedAdvisorConfig = advisorConfig
            )
        }

        "A default VulnerableCode AdvisorProvider should be configured" {
            val appConfig = ConfigFactory.empty()
            val providers = listOf("VulnerableCode")
            val advisorConfig = AdvisorConfiguration(
                vulnerableCode = VulnerableCodeConfiguration(serverUrl = null, apiKey = null)
            )

            testAdvisorCreation(
                existingProviders = providers,
                selectedProviders = providers,
                expectedProviders = providers,
                appConfig = appConfig,
                expectedAdvisorConfig = advisorConfig
            )
        }

        "A VulnerableCode AdvisorProvider with custom options should be configured" {
            val serverUrl = "https://vc.example.org/custom"
            val apiKey = "secret_key_to_access_the_database"
            val vcConfig = mapOf("serverUrl" to serverUrl, "apiKey" to apiKey)

            val appConfig = createConfigForProviders("VulnerableCode" to vcConfig)
            val providers = listOf("VulnerableCode")
            val advisorConfig = AdvisorConfiguration(vulnerableCode = VulnerableCodeConfiguration(serverUrl, apiKey))

            testAdvisorCreation(
                existingProviders = providers,
                selectedProviders = providers,
                expectedProviders = providers,
                appConfig = appConfig,
                expectedAdvisorConfig = advisorConfig
            )
        }

        "A default NexusIQ AdvisorProvider should be configured" {
            val serverUrl = "https://nexus.example.org/default"
            val nexusConfig = mapOf("serverUrl" to serverUrl)

            val appConfig = createConfigForProviders("NexusIq" to nexusConfig)
            val providers = listOf("NexusIq")
            val advisorConfig = AdvisorConfiguration(
                nexusIq = NexusIqConfiguration(
                    serverUrl = serverUrl,
                    browseUrl = serverUrl,
                    username = null,
                    password = null
                )
            )

            testAdvisorCreation(
                existingProviders = providers,
                selectedProviders = providers,
                expectedProviders = providers,
                appConfig = appConfig,
                expectedAdvisorConfig = advisorConfig
            )
        }

        "A NexusIQ AdvisorProvider with custom options should be configured" {
            val serverUrl = "https://nexus.example.org/custom"
            val browseUrl = "https://nexus.example.org/browse"
            val user = "nexus_user"
            val password = "nexus_pass"
            val nexusConfig = mapOf(
                "serverUrl" to serverUrl,
                "browseUrl" to browseUrl,
                "user" to user,
                "password" to password
            )

            val appConfig = createConfigForProviders("NexusIq" to nexusConfig)
            val providers = listOf("NexusIq")
            val advisorConfig = AdvisorConfiguration(
                nexusIq = NexusIqConfiguration(
                    serverUrl = serverUrl,
                    browseUrl = browseUrl,
                    username = user,
                    password = password
                )
            )

            testAdvisorCreation(
                existingProviders = providers,
                selectedProviders = providers,
                expectedProviders = providers,
                appConfig = appConfig,
                expectedAdvisorConfig = advisorConfig
            )
        }

        "An unknown AdvisorProvider should be dropped" {
            val appConfig = ConfigFactory.empty()
            val existingProviders = listOf("OSV", "VulnerableCode")
            val providers = listOf("OSV", "anUnknownProvider", "VulnerableCode")
            val advisorConfig = AdvisorConfiguration(
                osv = OsvConfiguration("https://api.osv.dev"),
                vulnerableCode = VulnerableCodeConfiguration(serverUrl = null, apiKey = null)
            )

            testAdvisorCreation(
                existingProviders = existingProviders,
                selectedProviders = providers,
                expectedProviders = existingProviders,
                appConfig = appConfig,
                expectedAdvisorConfig = advisorConfig
            )
        }

        "An AdvisorProvider which is not configured should be dropped" {
            val osvUrl = "https://osv.example.org/search"
            val vcUrl = "https://vc.example.org/search"
            val existingProviders = listOf("NexusIq", "OSV", "VulnerableCode")
            val expectedProviders = listOf("OSV", "VulnerableCode")

            val osvConfig = mapOf("serverUrl" to osvUrl)
            val vcConfig = mapOf("serverUrl" to vcUrl)
            val nexusConfig = mapOf(
                "browseUrl" to "https://nexus.example.org/browse",
                "user" to "nexus_user",
                "password" to "nexus_password"
            )
            val appConfig =
                createConfigForProviders("OSV" to osvConfig, "VulnerableCode" to vcConfig, "NexusIq" to nexusConfig)
            val advisorConfig = AdvisorConfiguration(
                osv = OsvConfiguration(serverUrl = osvUrl),
                vulnerableCode = VulnerableCodeConfiguration(serverUrl = vcUrl, apiKey = null)
            )

            testAdvisorCreation(
                existingProviders = existingProviders,
                selectedProviders = existingProviders,
                expectedProviders = expectedProviders,
                appConfig = appConfig,
                expectedAdvisorConfig = advisorConfig
            )
        }
    }

    /**
     * Test whether [AdvisorConfigurator] creates a correct [Advisor] instance based on the [providers on the
     * classpath][existingProviders], the [selectedProviders], the [configuration of the Advisor worker][appConfig].
     * Expect that the [Advisor] was created with the given list of [expectedProviders] and the given
     * [expectedAdvisorConfig].
     */
    private suspend fun testAdvisorCreation(
        existingProviders: Collection<String>,
        selectedProviders: Collection<String>,
        appConfig: Config,
        expectedProviders: List<String>,
        expectedAdvisorConfig: AdvisorConfiguration,
    ) {
        val providersMap = existingProviders.associateWith { mockk<AdviceProviderFactory>() }.toSortedMap()
        every { Advisor.ALL } returns providersMap

        // When mocking constructors, we have no access to the mocked instance. Therefore, this function defines
        // an expectation for a method invocation and then checks whether the correct result was returned.
        val packages = setOf(mockk<Package>())
        val advisorRun = mockk<AdvisorRun>()
        val expectedProviderFactories = expectedProviders.map(providersMap::getValue)
        coEvery {
            constructedWith<Advisor>(
                EqMatcher(expectedProviderFactories),
                EqMatcher(expectedAdvisorConfig)
            ).advise(packages)
        } returns advisorRun

        val configurator = AdvisorConfigurator(appConfig)
        val advisor = configurator.createAdvisor(selectedProviders)

        advisor.advise(packages) shouldBe advisorRun
    }
}

/**
 * Return a [Config] that combines the options of the given [providerConfigs]. Each passed in pair defines the
 * options for a single provider.
 */
private fun createConfigForProviders(vararg providerConfigs: Pair<String, Map<String, Any>>): Config {
    val providerConfigMap = providerConfigs.toMap()
    val appConfigMap = mapOf("providers" to providerConfigMap)

    return ConfigFactory.parseMap(appConfigMap)
}
