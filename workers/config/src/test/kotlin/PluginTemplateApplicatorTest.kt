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

package org.eclipse.apoapsis.ortserver.workers.config

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import io.mockk.every
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginAvailability
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginDescriptor
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.TemplateError
import org.eclipse.apoapsis.ortserver.model.AdvisorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.AnalyzerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.EvaluatorJobConfiguration
import org.eclipse.apoapsis.ortserver.model.JobConfigurations
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ReporterJobConfiguration
import org.eclipse.apoapsis.ortserver.model.ResolvablePluginConfig
import org.eclipse.apoapsis.ortserver.model.ResolvableProviderPluginConfig
import org.eclipse.apoapsis.ortserver.model.ResolvableSecret
import org.eclipse.apoapsis.ortserver.model.ScannerJobConfiguration
import org.eclipse.apoapsis.ortserver.model.SecretSource
import org.eclipse.apoapsis.ortserver.model.runs.PackageManagerConfiguration

private val organizationId = OrganizationId(1)

class PluginTemplateApplicatorTest : WordSpec({
    "applyTemplates" should {
        "apply templates only for the plugins that are configured to run" {
            val applicator = applicator(
                pluginTemplate(PluginType.ADVISOR, "OSV", option("serverUrl", "https://osv.example", true)),
                pluginTemplate(PluginType.ADVISOR, "VulnerableCode", option("serverUrl", "https://vc.example", true))
            )
            val jobConfigs = JobConfigurations(advisor = AdvisorJobConfiguration(advisors = listOf("OSV")))

            val result = applicator.applyTemplates(jobConfigs, organizationId)

            result.advisor?.config.shouldNotBeNull() shouldContainExactly mapOf(
                "OSV" to ResolvablePluginConfig(
                    options = mapOf("serverUrl" to "https://osv.example"),
                    secrets = emptyMap()
                )
            )
        }

        "apply a template if the configured plugin ID differs in case from the installed one" {
            val applicator = applicator(
                pluginTemplate(PluginType.ADVISOR, "OSV", option("serverUrl", "https://osv.example", true))
            )
            val jobConfigs = JobConfigurations(advisor = AdvisorJobConfiguration(advisors = listOf("osv")))

            val result = applicator.applyTemplates(jobConfigs, organizationId)

            result.advisor?.config.shouldNotBeNull() shouldContainExactly mapOf(
                "OSV" to ResolvablePluginConfig(
                    options = mapOf("serverUrl" to "https://osv.example"),
                    secrets = emptyMap()
                )
            )
        }

        "ignore plugins that are not installed" {
            val applicator = applicator(
                pluginTemplate(PluginType.ADVISOR, "OSV", option("serverUrl", "https://osv.example", true))
            )
            val jobConfigs = JobConfigurations(
                advisor = AdvisorJobConfiguration(advisors = listOf("OSV", "Unknown"))
            )

            val result = applicator.applyTemplates(jobConfigs, organizationId)

            result.advisor?.config.shouldNotBeNull() shouldContainExactly mapOf(
                "OSV" to ResolvablePluginConfig(
                    options = mapOf("serverUrl" to "https://osv.example"),
                    secrets = emptyMap()
                )
            )
        }

        "apply templates to project scanners" {
            val applicator = applicator(
                pluginTemplate(PluginType.SCANNER, "ScanCode", option("preferFileLicense", "true", true))
            )
            val jobConfigs = JobConfigurations(
                scanner = ScannerJobConfiguration(projectScanners = listOf("ScanCode"))
            )

            val result = applicator.applyTemplates(jobConfigs, organizationId)

            result.scanner?.config?.getValue("ScanCode")?.options.shouldNotBeNull() shouldContainExactly
                    mapOf("preferFileLicense" to "true")
        }

        "not apply templates to disabled package managers" {
            val applicator = applicator(
                pluginTemplate(PluginType.PACKAGE_MANAGER, "Maven", option("javaVersion", "17", true)),
                pluginTemplate(PluginType.PACKAGE_MANAGER, "NPM", option("legacyPeerDeps", "true", true))
            )
            val jobConfigs = JobConfigurations(
                analyzer = AnalyzerJobConfiguration(
                    enabledPackageManagers = listOf("Maven", "NPM"),
                    disabledPackageManagers = listOf("npm")
                )
            )

            val result = applicator.applyTemplates(jobConfigs, organizationId)

            result.analyzer.packageManagerOptions.shouldNotBeNull() shouldContainExactly mapOf(
                "Maven" to PackageManagerConfiguration(options = mapOf("javaVersion" to "17"))
            )
        }

        "apply templates to the default package managers if none are enabled explicitly" {
            val applicator = applicator(
                pluginTemplate(PluginType.PACKAGE_MANAGER, "Maven", option("javaVersion", "17", true))
            )
            val jobConfigs = JobConfigurations(analyzer = AnalyzerJobConfiguration(enabledPackageManagers = null))

            val result = applicator.applyTemplates(jobConfigs, organizationId)

            result.analyzer.packageManagerOptions.shouldNotBeNull() shouldContainExactly mapOf(
                "Maven" to PackageManagerConfiguration(options = mapOf("javaVersion" to "17"))
            )
        }

        "resolve the default package managers if none are enabled explicitly" {
            val applicator = applicator(
                pluginTemplate(PluginType.PACKAGE_MANAGER, "Maven"),
                pluginTemplate(PluginType.PACKAGE_MANAGER, "NPM")
            )
            val jobConfigs = JobConfigurations(analyzer = AnalyzerJobConfiguration(enabledPackageManagers = null))

            val result = applicator.applyTemplates(jobConfigs, organizationId)

            result.analyzer.enabledPackageManagers.shouldNotBeNull() shouldContainExactlyInAnyOrder
                    listOf("Maven", "NPM")
        }

        "not overwrite the explicitly enabled package managers" {
            val applicator = applicator(
                pluginTemplate(PluginType.PACKAGE_MANAGER, "Maven"),
                pluginTemplate(PluginType.PACKAGE_MANAGER, "NPM")
            )
            val jobConfigs = JobConfigurations(
                analyzer = AnalyzerJobConfiguration(enabledPackageManagers = listOf("Maven"))
            )

            val result = applicator.applyTemplates(jobConfigs, organizationId)

            result.analyzer.enabledPackageManagers.shouldNotBeNull() shouldContainExactly listOf("Maven")
        }

        "not remove the disabled package managers from the resolved default package managers" {
            val applicator = applicator(
                pluginTemplate(PluginType.PACKAGE_MANAGER, "Maven"),
                pluginTemplate(PluginType.PACKAGE_MANAGER, "NPM")
            )
            val jobConfigs = JobConfigurations(
                analyzer = AnalyzerJobConfiguration(
                    enabledPackageManagers = null,
                    disabledPackageManagers = listOf("NPM")
                )
            )

            val result = applicator.applyTemplates(jobConfigs, organizationId)

            result.analyzer.enabledPackageManagers.shouldNotBeNull() shouldContainExactlyInAnyOrder
                    listOf("Maven", "NPM")
        }

        "throw if a template cannot be fetched" {
            val templateService = mockk<PluginTemplateService> {
                every { getTemplateForOrganization(any(), any(), any()) } returns
                        Err(TemplateError.NotFound("Template not found."))
            }
            val pluginService = pluginService(PluginType.ADVISOR to "OSV")
            val jobConfigs = JobConfigurations(advisor = AdvisorJobConfiguration(advisors = listOf("OSV")))

            val exception = shouldThrow<IllegalStateException> {
                PluginTemplateApplicator(pluginService, templateService).applyTemplates(jobConfigs, organizationId)
            }

            exception.message shouldContain "ADVISOR"
            exception.message shouldContain "OSV"
            exception.message shouldContain "Template not found."
        }
    }

    "applyTemplatesToConfigs" should {
        "return the job configurations unchanged if there are no templates" {
            val jobConfigs = JobConfigurations(
                advisor = AdvisorJobConfiguration(advisors = listOf("OSV")),
                scanner = ScannerJobConfiguration(scanners = listOf("ScanCode"))
            )

            applyTemplatesToConfigs(jobConfigs, emptyMap()) shouldBe jobConfigs
        }

        "leave a plugin config unchanged if no template is configured for the plugin" {
            val config =
                ResolvablePluginConfig(options = mapOf("serverUrl" to "https://user.example"), secrets = emptyMap())
            val jobConfigs = JobConfigurations(
                advisor = AdvisorJobConfiguration(advisors = listOf("OSV"), config = mapOf("OSV" to config))
            )

            val result = applyTemplatesToConfigs(jobConfigs, templates(PluginType.ADVISOR, "OSV" to null))

            result shouldBe jobConfigs
        }

        "create a plugin config for a plugin without an existing config" {
            val template = pluginTemplate(PluginType.ADVISOR, "OSV", option("serverUrl", "https://osv.example", true))
            val jobConfigs = JobConfigurations(advisor = AdvisorJobConfiguration(advisors = listOf("OSV")))

            val result = applyTemplatesToConfigs(jobConfigs, templates(PluginType.ADVISOR, "OSV" to template))

            result.advisor?.config?.getValue("OSV")?.options.shouldNotBeNull() shouldContainExactly
                    mapOf("serverUrl" to "https://osv.example")
        }

        "override a user-provided option only if the template option is final" {
            val config = ResolvablePluginConfig(
                options = mapOf("serverUrl" to "https://user.example", "timeout" to "10"),
                secrets = emptyMap()
            )
            val template = pluginTemplate(
                PluginType.ADVISOR,
                "OSV",
                option("serverUrl", "https://admin.example", true),
                option("timeout", "30", false),
                option("retries", "3", false)
            )
            val jobConfigs = JobConfigurations(
                advisor = AdvisorJobConfiguration(advisors = listOf("OSV"), config = mapOf("OSV" to config))
            )

            val result = applyTemplatesToConfigs(jobConfigs, templates(PluginType.ADVISOR, "OSV" to template))

            result.advisor?.config?.getValue("OSV")?.options.shouldNotBeNull() shouldContainExactly mapOf(
                "serverUrl" to "https://admin.example",
                "timeout" to "10",
                "retries" to "3"
            )
        }

        "match plugin IDs ignoring case" {
            val config = ResolvablePluginConfig(options = mapOf("timeout" to "10"), secrets = emptyMap())
            val template = pluginTemplate(PluginType.ADVISOR, "OSV", option("serverUrl", "https://osv.example", true))
            val jobConfigs = JobConfigurations(
                advisor = AdvisorJobConfiguration(advisors = listOf("OSV"), config = mapOf("OSV" to config))
            )

            val result = applyTemplatesToConfigs(jobConfigs, templates(PluginType.ADVISOR, "osv" to template))

            result.advisor?.config.shouldNotBeNull() shouldContainExactly mapOf(
                "OSV" to ResolvablePluginConfig(
                    options = mapOf("timeout" to "10", "serverUrl" to "https://osv.example"),
                    secrets = emptyMap()
                )
            )
        }

        "add secret options with the admin secret source and override user-provided secrets if final" {
            val config = ResolvablePluginConfig(
                options = emptyMap(),
                secrets = mapOf("apiKey" to ResolvableSecret("user-key", SecretSource.USER))
            )
            val template = pluginTemplate(
                PluginType.ADVISOR,
                "OSV",
                secret("apiKey", "admin-key", true),
                secret("apiToken", "admin-token", false)
            )
            val jobConfigs = JobConfigurations(
                advisor = AdvisorJobConfiguration(advisors = listOf("OSV"), config = mapOf("OSV" to config))
            )

            val result = applyTemplatesToConfigs(jobConfigs, templates(PluginType.ADVISOR, "OSV" to template))

            result.advisor?.config?.getValue("OSV")?.secrets.shouldNotBeNull() shouldContainExactly mapOf(
                "apiKey" to ResolvableSecret("admin-key", SecretSource.ADMIN),
                "apiToken" to ResolvableSecret("admin-token", SecretSource.ADMIN)
            )
        }

        "not override a user-provided secret if the template option is not final" {
            val config = ResolvablePluginConfig(
                options = emptyMap(),
                secrets = mapOf("apiKey" to ResolvableSecret("user-key", SecretSource.USER))
            )
            val template = pluginTemplate(PluginType.ADVISOR, "OSV", secret("apiKey", "admin-key", false))
            val jobConfigs = JobConfigurations(
                advisor = AdvisorJobConfiguration(advisors = listOf("OSV"), config = mapOf("OSV" to config))
            )

            val result = applyTemplatesToConfigs(jobConfigs, templates(PluginType.ADVISOR, "OSV" to template))

            result.advisor?.config?.getValue("OSV")?.secrets.shouldNotBeNull() shouldContainExactly
                    mapOf("apiKey" to ResolvableSecret("user-key", SecretSource.USER))
        }

        "not create a plugin config if the template does not provide any value" {
            val template = pluginTemplate(PluginType.ADVISOR, "OSV", option("serverUrl", null, true))
            val jobConfigs = JobConfigurations(advisor = AdvisorJobConfiguration(advisors = listOf("OSV")))

            val result = applyTemplatesToConfigs(jobConfigs, templates(PluginType.ADVISOR, "OSV" to template))

            result.advisor?.config.shouldBeNull()
        }

        "not apply templates to jobs that are not enabled" {
            val template = pluginTemplate(PluginType.ADVISOR, "OSV", option("serverUrl", "https://osv.example", true))

            val result = applyTemplatesToConfigs(
                JobConfigurations(),
                templates(PluginType.ADVISOR, "OSV" to template)
            )

            result.advisor.shouldBeNull()
        }

        "create a package manager config for a package manager without an existing config" {
            val template = pluginTemplate(PluginType.PACKAGE_MANAGER, "Gradle", option("javaVersion", "17", true))
            val jobConfigs = JobConfigurations(
                analyzer = AnalyzerJobConfiguration(enabledPackageManagers = listOf("Gradle"))
            )

            val result =
                applyTemplatesToConfigs(jobConfigs, templates(PluginType.PACKAGE_MANAGER, "Gradle" to template))

            result.analyzer.packageManagerOptions?.getValue("Gradle") shouldBe
                    PackageManagerConfiguration(options = mapOf("javaVersion" to "17"))
        }

        "merge a template into an existing package manager config" {
            val config = PackageManagerConfiguration(
                mustRunAfter = listOf("Maven"),
                options = mapOf("javaVersion" to "11")
            )
            val template = pluginTemplate(
                PluginType.PACKAGE_MANAGER,
                "Gradle",
                option("javaVersion", "17", true),
                option("gradleVersion", "8", false)
            )
            val jobConfigs = JobConfigurations(
                analyzer = AnalyzerJobConfiguration(
                    enabledPackageManagers = listOf("Gradle"),
                    packageManagerOptions = mapOf("Gradle" to config)
                )
            )

            val result =
                applyTemplatesToConfigs(jobConfigs, templates(PluginType.PACKAGE_MANAGER, "Gradle" to template))

            result.analyzer.packageManagerOptions?.getValue("Gradle") shouldBe PackageManagerConfiguration(
                mustRunAfter = listOf("Maven"),
                options = mapOf("javaVersion" to "17", "gradleVersion" to "8")
            )
        }

        "ignore secret options of package manager templates" {
            val template = pluginTemplate(
                PluginType.PACKAGE_MANAGER,
                "Gradle",
                secret("token", "admin-token", true),
                option("javaVersion", "17", true)
            )
            val jobConfigs = JobConfigurations(
                analyzer = AnalyzerJobConfiguration(enabledPackageManagers = listOf("Gradle"))
            )

            val result =
                applyTemplatesToConfigs(jobConfigs, templates(PluginType.PACKAGE_MANAGER, "Gradle" to template))

            result.analyzer.packageManagerOptions?.getValue("Gradle") shouldBe
                    PackageManagerConfiguration(options = mapOf("javaVersion" to "17"))
        }

        "merge a template into all providers with a matching type" {
            val template = pluginTemplate(
                PluginType.PACKAGE_CURATION_PROVIDER,
                "OrtConfig",
                option("path", "/admin-curations", true),
                secret("token", "admin-token", true)
            )
            val jobConfigs = JobConfigurations(
                analyzer = AnalyzerJobConfiguration(
                    packageCurationProviders = listOf(
                        ResolvableProviderPluginConfig(type = "OrtConfig", id = "first"),
                        ResolvableProviderPluginConfig(type = "ortconfig", id = "second", enabled = false)
                    )
                )
            )

            val result = applyTemplatesToConfigs(
                jobConfigs,
                templates(PluginType.PACKAGE_CURATION_PROVIDER, "OrtConfig" to template)
            )

            result.analyzer.packageCurationProviders.forAll { provider ->
                provider.options shouldContainExactly mapOf("path" to "/admin-curations")
                provider.secrets shouldContainExactly
                        mapOf("token" to ResolvableSecret("admin-token", SecretSource.ADMIN))
            }
        }

        "not override a user-provided provider option if the template option is not final" {
            val provider = ResolvableProviderPluginConfig(
                type = "OrtConfig",
                options = mapOf("path" to "/user-curations")
            )
            val template = pluginTemplate(
                PluginType.PACKAGE_CURATION_PROVIDER,
                "OrtConfig",
                option("path", "/admin-curations", false)
            )
            val jobConfigs = JobConfigurations(
                analyzer = AnalyzerJobConfiguration(packageCurationProviders = listOf(provider))
            )

            val result = applyTemplatesToConfigs(
                jobConfigs,
                templates(PluginType.PACKAGE_CURATION_PROVIDER, "OrtConfig" to template)
            )

            result.analyzer.packageCurationProviders.single().options shouldContainExactly
                    mapOf("path" to "/user-curations")
        }

        "not add providers that are not configured" {
            val template = pluginTemplate(
                PluginType.PACKAGE_CURATION_PROVIDER,
                "OrtConfig",
                option("path", "/admin-curations", true)
            )
            val jobConfigs = JobConfigurations(analyzer = AnalyzerJobConfiguration())

            val result = applyTemplatesToConfigs(
                jobConfigs,
                templates(PluginType.PACKAGE_CURATION_PROVIDER, "OrtConfig" to template)
            )

            result.analyzer.packageCurationProviders.shouldBeEmpty()
        }

        "apply package configuration provider templates only to the job the provider is configured for" {
            val ortConfigTemplate = pluginTemplate(
                PluginType.PACKAGE_CONFIGURATION_PROVIDER,
                "OrtConfig",
                option("path", "/ort-config", true)
            )
            val defaultDirTemplate = pluginTemplate(
                PluginType.PACKAGE_CONFIGURATION_PROVIDER,
                "DefaultDir",
                option("path", "/default-dir", true)
            )
            val jobConfigs = JobConfigurations(
                evaluator = EvaluatorJobConfiguration(
                    packageConfigurationProviders = listOf(ResolvableProviderPluginConfig(type = "OrtConfig"))
                ),
                reporter = ReporterJobConfiguration(
                    packageConfigurationProviders = listOf(ResolvableProviderPluginConfig(type = "DefaultDir"))
                )
            )

            val result = applyTemplatesToConfigs(
                jobConfigs,
                templates(
                    PluginType.PACKAGE_CONFIGURATION_PROVIDER,
                    "OrtConfig" to ortConfigTemplate,
                    "DefaultDir" to defaultDirTemplate
                )
            )

            result.evaluator?.packageConfigurationProviders.shouldNotBeNull() shouldContainExactly listOf(
                ResolvableProviderPluginConfig(type = "OrtConfig", options = mapOf("path" to "/ort-config"))
            )
            result.reporter?.packageConfigurationProviders.shouldNotBeNull() shouldContainExactly listOf(
                ResolvableProviderPluginConfig(type = "DefaultDir", options = mapOf("path" to "/default-dir"))
            )
        }
    }
})

/**
 * Create a [PluginTemplateApplicator] that provides the given [templates]. The plugins the templates belong to are
 * reported as installed and enabled; like the production service, the template lookup fails for any other plugin.
 */
private fun applicator(vararg templates: PluginTemplate): PluginTemplateApplicator {
    val templatesByPlugin = templates.associateBy { it.pluginType to it.pluginId }

    val pluginTemplateService = mockk<PluginTemplateService> {
        every { getTemplateForOrganization(any(), any(), any()) } answers {
            val plugin = firstArg<PluginType>() to secondArg<String>()

            templatesByPlugin[plugin]?.let { template -> Ok(template) }
                ?: Err(TemplateError.InvalidPlugin("Plugin '${plugin.second}' is not installed."))
        }
    }

    return PluginTemplateApplicator(pluginService(*templatesByPlugin.keys.toTypedArray()), pluginTemplateService)
}

/** Create a mock of the [PluginService] that reports the given [plugins] as installed and enabled. */
private fun pluginService(vararg plugins: Pair<PluginType, String>) = mockk<PluginService> {
    every { getPlugins() } returns plugins.map { (pluginType, pluginId) ->
        PluginDescriptor(
            id = pluginId,
            type = pluginType,
            displayName = pluginId,
            summary = "summary",
            description = null,
            availability = PluginAvailability.ENABLED
        )
    }
}

private fun templates(pluginType: PluginType, vararg templates: Pair<String, PluginTemplate?>) =
    mapOf(pluginType to templates.toMap())

private fun pluginTemplate(pluginType: PluginType, pluginId: String, vararg options: PluginOptionTemplate) =
    PluginTemplate(
        name = "template",
        pluginType = pluginType,
        pluginId = pluginId,
        options = options.toList(),
        isGlobal = true
    )

private fun option(name: String, value: String?, isFinal: Boolean) =
    PluginOptionTemplate(name, PluginOptionType.STRING, value, isFinal)

private fun secret(name: String, value: String, isFinal: Boolean) =
    PluginOptionTemplate(name, PluginOptionType.SECRET, value, isFinal)
