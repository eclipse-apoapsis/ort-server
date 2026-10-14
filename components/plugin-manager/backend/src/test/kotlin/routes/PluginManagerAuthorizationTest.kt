/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.routes

import com.typesafe.config.ConfigFactory

import io.kotest.assertions.ktor.client.shouldHaveStatus

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route

import org.eclipse.apoapsis.ortserver.components.adminconfig.AdminConfigService
import org.eclipse.apoapsis.ortserver.components.authorization.rights.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.rights.Role
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.pluginManagerRoutes
import org.eclipse.apoapsis.ortserver.config.ConfigFileProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.ConfigSecretProviderFactoryForTesting
import org.eclipse.apoapsis.ortserver.model.CompoundHierarchyId
import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

class PluginManagerAuthorizationTest : AbstractAuthorizationTest({
    lateinit var configManager: ConfigManager
    lateinit var defaultPluginsService: DefaultPluginsService
    lateinit var pluginEventStore: PluginEventStore
    lateinit var pluginService: PluginService
    lateinit var pluginTemplateService: PluginTemplateService

    val pluginType = PluginType.ADVISOR
    val pluginId = VulnerableCodeFactory.descriptor.id
    val templateName = "test-template"

    var orgId = 0L
    lateinit var orgHierarchyId: CompoundHierarchyId

    beforeEach {
        orgId = dbExtension.fixtures.organization.id
        orgHierarchyId = CompoundHierarchyId.forOrganization(OrganizationId(orgId))

        val configMap = mapOf(
            ConfigManager.CONFIG_MANAGER_SECTION to mapOf(
                ConfigManager.FILE_PROVIDER_NAME_PROPERTY to ConfigFileProviderFactoryForTesting.NAME,
                ConfigManager.SECRET_PROVIDER_NAME_PROPERTY to ConfigSecretProviderFactoryForTesting.NAME
            )
        )

        configManager = ConfigManager.create(ConfigFactory.parseMap(configMap))
        pluginEventStore = PluginEventStore(dbExtension.db)
        pluginService = PluginService(dbExtension.db)
        val adminConfigService = AdminConfigService(configManager)
        pluginTemplateService = PluginTemplateService(
            dbExtension.db,
            PluginTemplateEventStore(dbExtension.db),
            pluginService,
            dbExtension.fixtures.organizationRepository,
            dbExtension.fixtures.repositoryRepository,
            adminConfigService
        )
        defaultPluginsService = DefaultPluginsService(
            dbExtension.db,
            DefaultPluginsEventStore(dbExtension.db),
            pluginService,
            pluginTemplateService,
            dbExtension.fixtures.organizationRepository,
            adminConfigService,
            configManager
        )
    }

    fun Route.routes() = pluginManagerRoutes(
        configManager,
        defaultPluginsService,
        pluginEventStore,
        pluginService,
        pluginTemplateService
    )

    fun requestShouldBeForbiddenForRole(role: Role, request: suspend HttpClient.() -> HttpResponse) {
        authorizationTestApplication({ routes() }) { _, testUserClient ->
            authorizationService.assignRole("test-user", role, orgHierarchyId)
            testUserClient.request() shouldHaveStatus HttpStatusCode.Forbidden
        }
    }

    "AddTemplateToOrganization" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.NotFound
            ) {
                post("/admin/plugins/$pluginType/$pluginId/templates/$templateName/addToOrganization?organizationId=1")
            }
        }
    }

    "DeleteTemplate" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.NotFound
            ) {
                delete("/admin/plugins/$pluginType/$pluginId/templates/$templateName")
            }
        }
    }

    "DisableGlobalTemplate" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.NotFound
            ) {
                post("/admin/plugins/$pluginType/$pluginId/templates/$templateName/disableGlobal")
            }
        }
    }

    "DisablePlugin" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.Accepted
            ) {
                post("/admin/plugins/$pluginType/$pluginId/disable")
            }
        }
    }

    "EnableGlobalTemplate" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.NotFound
            ) {
                post("/admin/plugins/$pluginType/$pluginId/templates/$templateName/enableGlobal")
            }
        }
    }

    "EnablePlugin" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.NotModified
            ) {
                post("/admin/plugins/$pluginType/$pluginId/enable")
            }
        }
    }

    "GetGlobalDefaultPlugins" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() }
            ) {
                get("/admin/plugins/defaults")
            }
        }
    }

    "GetInstalledPlugins" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() }
            ) {
                get("/admin/plugins")
            }
        }
    }

    "GetOrganizationDefaultPlugins" should {
        "require OrganizationPermission.READ" {
            requestShouldRequireRole(
                routes = { routes() },
                role = OrganizationRole.READER,
                hierarchyId = orgHierarchyId
            ) {
                get("/organizations/$orgId/plugins/defaults")
            }
        }
    }

    "GetTemplate" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.NotFound
            ) {
                get("/admin/plugins/$pluginType/$pluginId/templates/$templateName")
            }
        }
    }

    "GetTemplates" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.OK
            ) {
                get("/admin/plugins/$pluginType/$pluginId/templates")
            }
        }
    }

    "RemoveTemplateFromOrganization" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.NotFound
            ) {
                post(
                    "/admin/plugins/$pluginType/$pluginId/templates/$templateName" +
                            "/removeFromOrganization?organizationId=1"
                )
            }
        }
    }

    "ResetGlobalDefaultPlugins" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.NoContent
            ) {
                delete("/admin/plugins/defaults/$pluginType")
            }
        }
    }

    "ResetOrganizationDefaultPlugins" should {
        "require OrganizationPermission.MANAGE_DEFAULT_PLUGINS" {
            requestShouldRequireRole(
                routes = { routes() },
                role = OrganizationRole.ADMIN,
                hierarchyId = orgHierarchyId,
                successStatus = HttpStatusCode.NoContent
            ) {
                delete("/organizations/$orgId/plugins/defaults/$pluginType")
            }
        }

        "be forbidden for organization writers" {
            requestShouldBeForbiddenForRole(OrganizationRole.WRITER) {
                delete("/organizations/$orgId/plugins/defaults/$pluginType")
            }
        }
    }

    "RestrictPlugin" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.Accepted
            ) {
                post("/admin/plugins/$pluginType/$pluginId/restrict")
            }
        }
    }

    "SetGlobalDefaultPlugins" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.NoContent
            ) {
                put("/admin/plugins/defaults/$pluginType") {
                    setBody(listOf(pluginId))
                }
            }
        }
    }

    "SetOrganizationDefaultPlugins" should {
        "require OrganizationPermission.MANAGE_DEFAULT_PLUGINS" {
            requestShouldRequireRole(
                routes = { routes() },
                role = OrganizationRole.ADMIN,
                hierarchyId = orgHierarchyId,
                successStatus = HttpStatusCode.NoContent
            ) {
                put("/organizations/$orgId/plugins/defaults/$pluginType") {
                    setBody(listOf(pluginId))
                }
            }
        }

        "be forbidden for organization writers" {
            requestShouldBeForbiddenForRole(OrganizationRole.WRITER) {
                put("/organizations/$orgId/plugins/defaults/$pluginType") {
                    setBody(listOf(pluginId))
                }
            }
        }
    }

    "UpdateTemplateOptions" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { routes() },
                successStatus = HttpStatusCode.OK
            ) {
                post("/admin/plugins/$pluginType/$pluginId/templates/$templateName") {
                    setBody(emptyList<PluginOptionTemplate>())
                }
            }
        }
    }
})
