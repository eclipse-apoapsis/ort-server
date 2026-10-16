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

import com.typesafe.config.ConfigFactory

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
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

    "validatePluginConfigs" should {
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

            result.errors shouldHaveSize 1
            result.errors.single() shouldContain "'unknown' is not installed"
        }

        "reject report definitions using a disabled reporter" {
            pluginEventStore.appendEvent(PluginEvent(PluginType.REPORTER, "PdfTemplate", 1, PluginDisabled, "user"))

            val result = validateReporters("customTemplate")

            result.errors shouldHaveSize 1
            result.errors.single() shouldContain "'PdfTemplate' is disabled"
        }

        "reject report definitions using a restricted reporter without a template for the organization" {
            pluginEventStore.appendEvent(PluginEvent(PluginType.REPORTER, "PdfTemplate", 1, PluginRestricted, "user"))

            val result = validateReporters("customTemplate")

            result.errors shouldHaveSize 1
            result.errors.single() shouldContain "'PdfTemplate' is is disabled for this organization"
        }
    }
})
