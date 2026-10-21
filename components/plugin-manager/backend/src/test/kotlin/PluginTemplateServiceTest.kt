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

package org.eclipse.apoapsis.ortserver.components.pluginmanager

import com.github.michaelbull.result.get

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import org.eclipse.apoapsis.ortserver.components.adminconfig.AdminConfigService
import org.eclipse.apoapsis.ortserver.config.ConfigFileProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.config.RequestedConfigContext
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ResolvablePluginConfig

/** The config context containing an admin config with report definitions. */
private const val REPORT_DEFINITIONS_CONTEXT = "src/test/resources/config"

private val emptyConfig = ResolvablePluginConfig(emptyMap(), emptyMap())

class PluginTemplateServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var pluginEventStore: PluginEventStore
    lateinit var service: PluginTemplateService
    lateinit var configManager: ConfigManager
    var organizationId = OrganizationId(-1L)

    beforeEach {
        val configMap = mapOf(
            ConfigManager.CONFIG_MANAGER_SECTION to mapOf(
                ConfigManager.FILE_PROVIDER_NAME_PROPERTY to ConfigFileProviderFactoryForTesting.NAME,
                ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME
            )
        )

        configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))

        pluginEventStore = PluginEventStore(dbExtension.db)
        service = PluginTemplateService(
            dbExtension.db,
            PluginTemplateEventStore(dbExtension.db),
            PluginService(dbExtension.db),
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.repositoryRepository,
            AdminConfigService(configManager)
        )

        organizationId = OrganizationId(dbExtension.fixtures.organization.id)
    }

    suspend fun validateReporters(vararg reporters: String) =
        service.validatePluginConfigs(
            mapOf(PluginType.REPORTER to reporters.associateWith { emptyConfig }),
            organizationId,
            configManager.resolveContext(RequestedConfigContext(REPORT_DEFINITIONS_CONTEXT))
        )

    suspend fun createTemplate(name: String, pluginType: PluginType, pluginId: String) =
        service.create(name, pluginType, pluginId, "user", emptyList()).isOk shouldBe true

    suspend fun availablePlugin(organizationId: OrganizationId?, pluginType: PluginType, pluginId: String) =
        service.getAvailablePlugins(organizationId).singleOrNull {
            it.plugin.type == pluginType && it.plugin.id == pluginId
        }

    "getAvailablePlugins()" should {
        "exclude disabled plugins" {
            pluginEventStore.appendEvent(PluginEvent(PluginType.ADVISOR, "OSV", 1, PluginDisabled, "user"))

            service.getAvailablePlugins(organizationId).map { it.plugin.id } shouldNotContain "OSV"
            service.getAvailablePlugins(null).map { it.plugin.id } shouldNotContain "OSV"
        }

        "exclude restricted plugins without a template for the organization" {
            pluginEventStore.appendEvent(PluginEvent(PluginType.ADVISOR, "OSV", 1, PluginRestricted, "user"))

            availablePlugin(organizationId, PluginType.ADVISOR, "OSV") should beNull()
        }

        "include restricted plugins if no organization is given" {
            pluginEventStore.appendEvent(PluginEvent(PluginType.ADVISOR, "OSV", 1, PluginRestricted, "user"))

            service.getAvailablePlugins(null).map { it.plugin.id } shouldContain "OSV"
        }

        "include restricted plugins with a global template" {
            pluginEventStore.appendEvent(PluginEvent(PluginType.ADVISOR, "OSV", 1, PluginRestricted, "user"))
            createTemplate("global", PluginType.ADVISOR, "OSV")
            service.enableGlobal("global", PluginType.ADVISOR, "OSV", "user").isOk shouldBe true

            availablePlugin(organizationId, PluginType.ADVISOR, "OSV")?.template?.name shouldBe "global"
        }

        "prefer the template assigned to the organization over the global template" {
            createTemplate("global", PluginType.ADVISOR, "OSV")
            service.enableGlobal("global", PluginType.ADVISOR, "OSV", "user").isOk shouldBe true
            createTemplate("organization", PluginType.ADVISOR, "OSV")
            service.addOrganization("organization", PluginType.ADVISOR, "OSV", organizationId, "user").isOk shouldBe
                    true

            availablePlugin(organizationId, PluginType.ADVISOR, "OSV")?.template?.name shouldBe "organization"
        }

        "not return templates if no organization is given" {
            createTemplate("global", PluginType.ADVISOR, "OSV")
            service.enableGlobal("global", PluginType.ADVISOR, "OSV", "user").isOk shouldBe true

            availablePlugin(null, PluginType.ADVISOR, "OSV")?.template should beNull()
        }
    }

    "validatePluginConfigs()" should {
        "accept installed reporters" {
            val result = validateReporters("CycloneDX", "WebApp")

            result.errors should beEmpty()
        }

        "accept the names of report definitions" {
            val result = validateReporters("customTemplate", "WEBAPP")

            result.errors should beEmpty()
        }

        "reject reporters that are neither installed nor report definitions" {
            val result = validateReporters("unknown")

            result.errors shouldBeSingleton {
                it shouldContain "'unknown' is not installed"
            }
        }

        "reject report definitions using a disabled reporter" {
            pluginEventStore.appendEvent(PluginEvent(PluginType.REPORTER, "PdfTemplate", 1, PluginDisabled, "user"))

            val result = validateReporters("customTemplate")

            result.errors shouldBeSingleton {
                it shouldContain "'PdfTemplate' is disabled"
            }
        }

        "reject report definitions using a restricted reporter without a template for the organization" {
            pluginEventStore.appendEvent(PluginEvent(PluginType.REPORTER, "PdfTemplate", 1, PluginRestricted, "user"))

            val result = validateReporters("customTemplate")

            result.errors shouldBeSingleton {
                it shouldContain "'PdfTemplate' is is disabled for this organization"
            }
        }

        "not check the options of report definitions against final template options" {
            val themeOption = PluginOptionTemplate(
                option = "pdfThemeFile",
                type = PluginOptionType.STRING,
                value = "theme.yml",
                isFinal = true
            )
            service.create("global", PluginType.REPORTER, "PdfTemplate", "user", listOf(themeOption)).isOk shouldBe
                    true
            service.enableGlobal("global", PluginType.REPORTER, "PdfTemplate", "user").isOk shouldBe true

            val config = ResolvablePluginConfig(mapOf("pdfThemeFile" to "other.yml"), emptyMap())
            val context = configManager.resolveContext(RequestedConfigContext(REPORT_DEFINITIONS_CONTEXT))

            service.validatePluginConfigs(
                mapOf(PluginType.REPORTER to mapOf("customTemplate" to config)),
                organizationId,
                context
            ).errors should beEmpty()

            service.validatePluginConfigs(
                mapOf(PluginType.REPORTER to mapOf("PdfTemplate" to config)),
                organizationId,
                context
            ).errors shouldBeSingleton {
                it shouldContain "is set to a fixed value"
            }
        }
    }

    "templates stored with a different spelling of the plugin ID" should {
        val serverUrlOption = PluginOptionTemplate(
            option = "serverUrl",
            type = PluginOptionType.STRING,
            value = "https://osv.example.com",
            isFinal = true
        )

        // Simulate a template that was stored with a different spelling of the plugin ID, e.g. by an older ORT
        // version.
        suspend fun createLegacyTemplate(name: String) {
            val eventStore = PluginTemplateEventStore(dbExtension.db)
            eventStore.appendEvent(
                PluginTemplateEvent(name, PluginType.ADVISOR, "osv", 1, OptionsUpdated(listOf(serverUrlOption)), "user")
            )
            eventStore.appendEvent(PluginTemplateEvent(name, PluginType.ADVISOR, "osv", 2, GlobalEnabled, "user"))
        }

        "be found" {
            createLegacyTemplate("legacy")

            service.getTemplateForOrganization(PluginType.ADVISOR, "OSV", organizationId).get()?.name shouldBe
                    "legacy"
            service.getTemplates(PluginType.ADVISOR, "OSV").get()?.map { it.name } shouldBe listOf("legacy")
            availablePlugin(organizationId, PluginType.ADVISOR, "OSV")?.template?.name shouldBe "legacy"
        }

        "be enforced when validating plugin configs" {
            createLegacyTemplate("legacy")

            val result = service.validatePluginConfigs(
                mapOf(
                    PluginType.ADVISOR to mapOf(
                        "OSV" to ResolvablePluginConfig(mapOf("serverUrl" to "https://other.example.com"), emptyMap())
                    )
                ),
                organizationId,
                configManager.resolveContext(RequestedConfigContext(REPORT_DEFINITIONS_CONTEXT))
            )

            result.errors shouldBeSingleton {
                it shouldContain "is set to a fixed value"
            }
        }

        "be updated using the current spelling" {
            createLegacyTemplate("legacy")

            service.disableGlobal("legacy", PluginType.ADVISOR, "OSV", "user").isOk shouldBe true

            service.getTemplateForOrganization(PluginType.ADVISOR, "OSV", organizationId).get() should beNull()
            service.getTemplates(PluginType.ADVISOR, "OSV").get()?.map { it.pluginId } shouldBe listOf("OSV")
        }

        "prevent creating a template with the same name" {
            createLegacyTemplate("legacy")

            service.create("legacy", PluginType.ADVISOR, "OSV", "user", emptyList()).isOk shouldBe false
        }

        "prevent another global template" {
            createLegacyTemplate("legacy")
            createTemplate("new", PluginType.ADVISOR, "OSV")

            service.enableGlobal("new", PluginType.ADVISOR, "OSV", "user").isOk shouldBe false
        }

        "prevent another template for the same organization" {
            createLegacyTemplate("legacy")
            PluginTemplateEventStore(dbExtension.db).appendEvent(
                PluginTemplateEvent("legacy", PluginType.ADVISOR, "osv", 3, OrganizationAdded(organizationId), "user")
            )
            createTemplate("new", PluginType.ADVISOR, "OSV")

            shouldThrowAny {
                service.addOrganization("new", PluginType.ADVISOR, "OSV", organizationId, "user")
            }
        }
    }
})
