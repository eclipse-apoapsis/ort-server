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

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getError
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.fail
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

import io.mockk.coEvery
import io.mockk.spyk

import org.eclipse.apoapsis.ortserver.components.adminconfig.AdminConfigService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.ADVISOR
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.PACKAGE_CURATION_PROVIDER
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.PACKAGE_MANAGER
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.PROJECT_SCANNER
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.REPORTER
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.SCANNER
import org.eclipse.apoapsis.ortserver.config.ConfigFileProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.config.RequestedConfigContext
import org.eclipse.apoapsis.ortserver.config.ResolvedConfigContext
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.RepositoryId

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration

/** The config context containing an admin config with report definitions. */
private const val REPORT_DEFINITIONS_CONTEXT = "src/test/resources/config"

/** A config context without an admin config. */
private const val EMPTY_CONTEXT = "non-existing"

class DefaultPluginsServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var pluginEventStore: PluginEventStore
    lateinit var pluginTemplateService: PluginTemplateService
    lateinit var configManager: ConfigManager
    lateinit var service: DefaultPluginsService
    var organizationId = OrganizationId(-1L)

    val curationProviderIds = getInstalledPlugins(PluginType.PACKAGE_CURATION_PROVIDER).map { it.id }

    beforeEach {
        val configMap = mapOf(
            ConfigManager.CONFIG_MANAGER_SECTION to mapOf(
                ConfigManager.FILE_PROVIDER_NAME_PROPERTY to ConfigFileProviderFactoryForTesting.NAME,
                ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME
            )
        )

        val realConfigManager = ConfigManager.create(ConfigFactory.parseMap(configMap))
        configManager = spyk(realConfigManager) {
            // Use the config with report definitions as default config context.
            coEvery { resolveContext(RequestedConfigContext.EMPTY) } coAnswers {
                realConfigManager.resolveContext(RequestedConfigContext(REPORT_DEFINITIONS_CONTEXT))
            }
        }

        val adminConfigService = AdminConfigService(configManager)
        val pluginService = PluginService(dbExtension.db)

        pluginEventStore = PluginEventStore(dbExtension.db)
        pluginTemplateService = PluginTemplateService(
            dbExtension.db,
            PluginTemplateEventStore(dbExtension.db),
            pluginService,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.repositoryRepository,
            adminConfigService
        )

        service = DefaultPluginsService(
            dbExtension.db,
            DefaultPluginsEventStore(dbExtension.db),
            pluginService,
            pluginTemplateService,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.repositoryRepository,
            adminConfigService,
            configManager
        )

        organizationId = OrganizationId(dbExtension.fixtures.organization.id)
    }

    suspend fun context(name: String) = configManager.resolveContext(RequestedConfigContext(name))

    suspend fun resolve(category: DefaultPluginsCategory, context: ResolvedConfigContext): DefaultPlugins =
        service.resolveDefaultPlugins(organizationId, context).shouldBeOk().single { it.category == category }

    suspend fun getDefaultPlugins(organizationId: OrganizationId?, category: DefaultPluginsCategory) =
        service.getAllDefaultPlugins(organizationId).map { defaults -> defaults.single { it.category == category } }

    suspend fun disable(pluginType: PluginType, pluginId: String) =
        pluginEventStore.appendEvent(PluginEvent(pluginType, pluginId, 1, PluginDisabled, "user"))

    suspend fun restrict(pluginType: PluginType, pluginId: String) =
        pluginEventStore.appendEvent(PluginEvent(pluginType, pluginId, 1, PluginRestricted, "user"))

    "getAllDefaultPlugins" should {
        "return the default plugins of all categories" {
            service.getAllDefaultPlugins(organizationId).shouldBeOk().map { it.category } shouldBe
                    DefaultPluginsCategory.entries
        }

        "return the built-in defaults if no defaults are configured" {
            getDefaultPlugins(null, ADVISOR).shouldBeOk() shouldBe
                    DefaultPlugins(ADVISOR, listOf("OSV", "VulnerableCode"), DefaultPluginsSource.BUILT_IN)
            getDefaultPlugins(organizationId, SCANNER).shouldBeOk() shouldBe
                    DefaultPlugins(SCANNER, listOf("ScanCode"), DefaultPluginsSource.BUILT_IN)
            getDefaultPlugins(null, PACKAGE_CURATION_PROVIDER).shouldBeOk() shouldBe
                    DefaultPlugins(PACKAGE_CURATION_PROVIDER, emptyList(), DefaultPluginsSource.BUILT_IN)
        }

        "return the package managers enabled by default in ORT as built-in defaults" {
            val defaults = getDefaultPlugins(null, PACKAGE_MANAGER).shouldBeOk()

            defaults.pluginIds.map { it.lowercase() } shouldBe
                    AnalyzerConfiguration().enabledPackageManagers.map { it.lowercase() }
            defaults.source shouldBe DefaultPluginsSource.BUILT_IN
        }

        "return the global defaults for organizations without configured defaults" {
            service.setDefaultPlugins(null, ADVISOR, listOf("VulnerableCode"), "user").shouldBeOk()

            getDefaultPlugins(null, ADVISOR).shouldBeOk() shouldBe
                    DefaultPlugins(ADVISOR, listOf("VulnerableCode"), DefaultPluginsSource.GLOBAL)
            getDefaultPlugins(organizationId, ADVISOR).shouldBeOk() shouldBe
                    DefaultPlugins(ADVISOR, listOf("VulnerableCode"), DefaultPluginsSource.GLOBAL)
        }

        "prefer the organization defaults over the global defaults" {
            service.setDefaultPlugins(null, ADVISOR, listOf("VulnerableCode"), "user").shouldBeOk()
            service.setDefaultPlugins(organizationId, ADVISOR, emptyList(), "user").shouldBeOk()

            getDefaultPlugins(organizationId, ADVISOR).shouldBeOk() shouldBe
                    DefaultPlugins(ADVISOR, emptyList(), DefaultPluginsSource.ORGANIZATION)
        }

        "return the built-in package scanners as built-in defaults for project scanners" {
            getDefaultPlugins(organizationId, PROJECT_SCANNER).shouldBeOk() shouldBe
                    DefaultPlugins(PROJECT_SCANNER, listOf("ScanCode"), DefaultPluginsSource.BUILT_IN, SCANNER)
        }

        "fall back to the package scanners of the same level for project scanners" {
            service.setDefaultPlugins(null, SCANNER, emptyList(), "user").shouldBeOk()

            getDefaultPlugins(null, PROJECT_SCANNER).shouldBeOk() shouldBe
                    DefaultPlugins(PROJECT_SCANNER, emptyList(), DefaultPluginsSource.GLOBAL, SCANNER)
            getDefaultPlugins(organizationId, PROJECT_SCANNER).shouldBeOk() shouldBe
                    DefaultPlugins(PROJECT_SCANNER, emptyList(), DefaultPluginsSource.GLOBAL, SCANNER)
        }

        "prefer the package scanners of the organization over the global project scanners" {
            service.setDefaultPlugins(null, PROJECT_SCANNER, emptyList(), "user").shouldBeOk()
            service.setDefaultPlugins(organizationId, SCANNER, listOf("ScanCode"), "user").shouldBeOk()

            getDefaultPlugins(null, PROJECT_SCANNER).shouldBeOk() shouldBe
                    DefaultPlugins(PROJECT_SCANNER, emptyList(), DefaultPluginsSource.GLOBAL)
            getDefaultPlugins(organizationId, PROJECT_SCANNER).shouldBeOk() shouldBe
                    DefaultPlugins(PROJECT_SCANNER, listOf("ScanCode"), DefaultPluginsSource.ORGANIZATION, SCANNER)
        }

        "prefer the project scanners of the organization over its package scanners" {
            service.setDefaultPlugins(organizationId, SCANNER, listOf("ScanCode"), "user").shouldBeOk()
            service.setDefaultPlugins(organizationId, PROJECT_SCANNER, emptyList(), "user").shouldBeOk()

            getDefaultPlugins(organizationId, PROJECT_SCANNER).shouldBeOk() shouldBe
                    DefaultPlugins(PROJECT_SCANNER, emptyList(), DefaultPluginsSource.ORGANIZATION)
        }

        "return configured project scanners independently of the package scanners" {
            service.setDefaultPlugins(null, PROJECT_SCANNER, emptyList(), "user").shouldBeOk()

            getDefaultPlugins(organizationId, PROJECT_SCANNER).shouldBeOk() shouldBe
                    DefaultPlugins(PROJECT_SCANNER, emptyList(), DefaultPluginsSource.GLOBAL)
            getDefaultPlugins(organizationId, SCANNER).shouldBeOk() shouldBe
                    DefaultPlugins(SCANNER, listOf("ScanCode"), DefaultPluginsSource.BUILT_IN)
        }

        "fail if the organization does not exist" {
            getDefaultPlugins(OrganizationId(-1L), ADVISOR)
                .shouldBeErr().shouldBeInstanceOf<DefaultPluginsError.NotFound>()
        }
    }

    "setDefaultPlugins" should {
        "normalize the plugin IDs and keep their order" {
            val ids = curationProviderIds.take(2).reversed()

            service.setDefaultPlugins(
                null,
                PACKAGE_CURATION_PROVIDER,
                ids.map { it.uppercase() },
                "user"
            ).shouldBeOk()

            getDefaultPlugins(null, PACKAGE_CURATION_PROVIDER).shouldBeOk().pluginIds shouldBe ids
        }

        "fail for plugins that are not installed" {
            service.setDefaultPlugins(null, ADVISOR, listOf("unknown"), "user")
                .shouldBeErr().shouldBeInstanceOf<DefaultPluginsError.InvalidPlugin>()
                .message shouldContain "No plugin with type 'ADVISOR' and ID 'unknown' found."
        }

        "validate project scanners as scanner plugins" {
            service.setDefaultPlugins(null, PROJECT_SCANNER, listOf("scancode"), "user")
                .shouldBeOk()
            getDefaultPlugins(null, PROJECT_SCANNER).shouldBeOk() shouldBe
                    DefaultPlugins(PROJECT_SCANNER, listOf("ScanCode"), DefaultPluginsSource.GLOBAL)

            service.setDefaultPlugins(null, PROJECT_SCANNER, listOf("OSV"), "user")
                .shouldBeErr().shouldBeInstanceOf<DefaultPluginsError.InvalidPlugin>()
                .message shouldContain "No plugin with type 'SCANNER' and ID 'OSV' found."
        }

        "fail for duplicate plugin IDs" {
            service.setDefaultPlugins(null, ADVISOR, listOf("OSV", "osv"), "user")
                .shouldBeErr().shouldBeInstanceOf<DefaultPluginsError.InvalidPlugin>()
                .message shouldContain "multiple times"
        }

        "fail for disabled plugins" {
            disable(PluginType.ADVISOR, "OSV")

            service.setDefaultPlugins(null, ADVISOR, listOf("OSV"), "user")
                .shouldBeErr().shouldBeInstanceOf<DefaultPluginsError.InvalidPlugin>()
                .message shouldContain "is not available"
        }

        "accept restricted plugins globally" {
            restrict(PluginType.ADVISOR, "OSV")

            service.setDefaultPlugins(null, ADVISOR, listOf("OSV"), "user").shouldBeOk()
        }

        "fail for restricted plugins without a template for the organization" {
            restrict(PluginType.ADVISOR, "OSV")

            service.setDefaultPlugins(organizationId, ADVISOR, listOf("OSV"), "user")
                .shouldBeErr().shouldBeInstanceOf<DefaultPluginsError.InvalidPlugin>()
        }

        "accept restricted plugins with a template for the organization" {
            restrict(PluginType.ADVISOR, "OSV")
            pluginTemplateService.create("template", PluginType.ADVISOR, "OSV", "user", emptyList()).shouldBeOk()
            pluginTemplateService.addOrganization("template", PluginType.ADVISOR, "OSV", organizationId, "user")
                .shouldBeOk()

            service.setDefaultPlugins(organizationId, ADVISOR, listOf("OSV"), "user").shouldBeOk()
        }

        "accept report definitions from the default config context" {
            service.setDefaultPlugins(null, REPORTER, listOf("CUSTOMTEMPLATE", "PdfTemplate"), "user")
                .shouldBeOk()

            getDefaultPlugins(null, REPORTER).shouldBeOk().pluginIds shouldBe
                    listOf("customTemplate", "PdfTemplate")
        }

        "fail for report definitions which use disabled reporters" {
            disable(PluginType.REPORTER, "PdfTemplate")

            service.setDefaultPlugins(null, REPORTER, listOf("customTemplate"), "user")
                .shouldBeErr().shouldBeInstanceOf<DefaultPluginsError.InvalidPlugin>()
        }

        "fail if the organization does not exist" {
            service.setDefaultPlugins(OrganizationId(-1L), ADVISOR, listOf("OSV"), "user")
                .shouldBeErr().shouldBeInstanceOf<DefaultPluginsError.NotFound>()
        }

        "not create a new event if the plugins did not change" {
            val eventStore = DefaultPluginsEventStore(dbExtension.db)

            service.setDefaultPlugins(null, ADVISOR, listOf("OSV"), "user").shouldBeOk()
            service.setDefaultPlugins(null, ADVISOR, listOf("osv"), "user").shouldBeOk()

            eventStore.loadState(null, ADVISOR).version shouldBe 1
        }
    }

    "resetDefaultPlugins" should {
        "fall back to the global defaults for an organization" {
            service.setDefaultPlugins(null, ADVISOR, listOf("VulnerableCode"), "user").shouldBeOk()
            service.setDefaultPlugins(organizationId, ADVISOR, listOf("OSV"), "user").shouldBeOk()

            service.resetDefaultPlugins(organizationId, ADVISOR, "user").shouldBeOk()

            getDefaultPlugins(organizationId, ADVISOR).shouldBeOk() shouldBe
                    DefaultPlugins(ADVISOR, listOf("VulnerableCode"), DefaultPluginsSource.GLOBAL)
        }

        "fall back to the built-in defaults globally" {
            service.setDefaultPlugins(null, ADVISOR, listOf("VulnerableCode"), "user").shouldBeOk()

            service.resetDefaultPlugins(null, ADVISOR, "user").shouldBeOk()

            getDefaultPlugins(null, ADVISOR).shouldBeOk().source shouldBe
                    DefaultPluginsSource.BUILT_IN
        }

        "do nothing if no defaults are configured" {
            service.resetDefaultPlugins(null, ADVISOR, "user").shouldBeOk()

            DefaultPluginsEventStore(dbExtension.db).loadState(null, ADVISOR).version shouldBe 0
        }
    }

    "resolveDefaultPlugins" should {
        "return defaults for all plugin types" {
            val defaults = service.resolveDefaultPlugins(organizationId, context(EMPTY_CONTEXT)).shouldBeOk()

            defaults shouldHaveSize DefaultPluginsCategory.entries.size
            defaults.map { it.category }.toSet() shouldBe DefaultPluginsCategory.entries.toSet()
        }

        "remove disabled plugins" {
            service.setDefaultPlugins(null, ADVISOR, listOf("OSV", "VulnerableCode"), "user").shouldBeOk()
            disable(PluginType.ADVISOR, "OSV")

            resolve(ADVISOR, context(EMPTY_CONTEXT)) shouldBe
                    DefaultPlugins(ADVISOR, listOf("VulnerableCode"), DefaultPluginsSource.GLOBAL)
        }

        "remove restricted plugins without a template for the organization" {
            restrict(PluginType.ADVISOR, "OSV")

            resolve(ADVISOR, context(EMPTY_CONTEXT)).pluginIds shouldBe listOf("VulnerableCode")
        }

        "keep restricted plugins with a template for the organization" {
            restrict(PluginType.ADVISOR, "OSV")
            pluginTemplateService.create("template", PluginType.ADVISOR, "OSV", "user", emptyList()).shouldBeOk()
            pluginTemplateService.enableGlobal("template", PluginType.ADVISOR, "OSV", "user").shouldBeOk()

            resolve(ADVISOR, context(EMPTY_CONTEXT)).pluginIds shouldBe listOf("OSV", "VulnerableCode")
        }

        "remove report definitions which do not exist in the config context" {
            service.setDefaultPlugins(null, REPORTER, listOf("customTemplate", "CycloneDX"), "user")
                .shouldBeOk()

            resolve(REPORTER, context(EMPTY_CONTEXT)).pluginIds shouldBe listOf("CycloneDX")
            resolve(REPORTER, context(REPORT_DEFINITIONS_CONTEXT)).pluginIds shouldBe
                    listOf("customTemplate", "CycloneDX")
        }

        "remove report definitions which use reporters that are not available for the organization" {
            service.setDefaultPlugins(null, REPORTER, listOf("customTemplate", "webApp"), "user").shouldBeOk()
            restrict(PluginType.REPORTER, "PdfTemplate")
            disable(PluginType.REPORTER, "WebApp")

            resolve(REPORTER, context(REPORT_DEFINITIONS_CONTEXT)).pluginIds should beEmpty()
        }

        "keep report definitions which use restricted reporters with a template for the organization" {
            service.setDefaultPlugins(null, REPORTER, listOf("customTemplate"), "user").shouldBeOk()
            restrict(PluginType.REPORTER, "PdfTemplate")
            pluginTemplateService.create("template", PluginType.REPORTER, "PdfTemplate", "user", emptyList())
                .shouldBeOk()
            pluginTemplateService.enableGlobal("template", PluginType.REPORTER, "PdfTemplate", "user").shouldBeOk()

            resolve(REPORTER, context(REPORT_DEFINITIONS_CONTEXT)).pluginIds shouldBe listOf("customTemplate")
        }

        "resolve the project scanners from the package scanners of the same level" {
            service.setDefaultPlugins(organizationId, SCANNER, listOf("ScanCode"), "user").shouldBeOk()

            resolve(PROJECT_SCANNER, context(EMPTY_CONTEXT)) shouldBe
                    DefaultPlugins(PROJECT_SCANNER, listOf("ScanCode"), DefaultPluginsSource.ORGANIZATION, SCANNER)
        }

        "remove reporter plugins which are replaced by report definitions in the config context" {
            service.setDefaultPlugins(null, REPORTER, listOf("PdfTemplate", "CycloneDX"), "user")
                .shouldBeOk()

            resolve(REPORTER, context(EMPTY_CONTEXT)).pluginIds shouldBe listOf("PdfTemplate", "CycloneDX")
            resolve(REPORTER, context(REPORT_DEFINITIONS_CONTEXT)).pluginIds shouldBe listOf("CycloneDX")
        }

        "keep the order of ordered plugin types" {
            val ids = curationProviderIds.take(2).reversed()
            service.setDefaultPlugins(organizationId, PACKAGE_CURATION_PROVIDER, ids, "user")
                .shouldBeOk()

            resolve(PACKAGE_CURATION_PROVIDER, context(EMPTY_CONTEXT)) shouldBe
                    DefaultPlugins(PACKAGE_CURATION_PROVIDER, ids, DefaultPluginsSource.ORGANIZATION)
        }
    }

    "resolveDefaultPluginsForRepository" should {
        "resolve the defaults of the organization of the repository" {
            service.setDefaultPlugins(organizationId, ADVISOR, listOf("OSV"), "user").shouldBeOk()
            val repositoryId = RepositoryId(dbExtension.fixtures.repository.id)

            val defaults = service.resolveDefaultPluginsForRepository(repositoryId, context(EMPTY_CONTEXT))
                .shouldBeOk()

            defaults.single { it.category == ADVISOR } shouldBe
                    DefaultPlugins(ADVISOR, listOf("OSV"), DefaultPluginsSource.ORGANIZATION)
        }

        "return NotFound if the repository does not exist" {
            service.resolveDefaultPluginsForRepository(RepositoryId(-1L), context(EMPTY_CONTEXT))
                .shouldBeErr().shouldBeInstanceOf<DefaultPluginsError.NotFound>()
        }
    }
})

private fun <V, E> Result<V, E>.shouldBeOk(): V = getOrElse { fail("Expected an Ok result, but got error '$it'.") }

private fun <V, E> Result<V, E>.shouldBeErr(): E = getError() ?: fail("Expected an error result, but got '$this'.")
