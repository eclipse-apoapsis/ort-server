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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.endpoints

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginOptionTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.components.pluginmanager.pluginManagerRoutes
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

class PluginManagerAuthorizationTest : AbstractAuthorizationTest({
    lateinit var pluginEventStore: PluginEventStore
    lateinit var pluginService: PluginService
    lateinit var pluginTemplateService: PluginTemplateService

    val pluginType = PluginType.ADVISOR
    val pluginId = VulnerableCodeFactory.descriptor.id
    val templateName = "test-template"

    beforeEach {
        pluginEventStore = PluginEventStore(dbExtension.db)
        pluginService = PluginService(dbExtension.db)
        pluginTemplateService = PluginTemplateService(
            dbExtension.db,
            PluginTemplateEventStore(dbExtension.db),
            pluginService,
            dbExtension.fixtures.organizationRepository
        )
    }

    "AddTemplateToOrganization" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) },
                successStatus = HttpStatusCode.NotFound
            ) {
                post("/admin/plugins/$pluginType/$pluginId/templates/$templateName/addToOrganization?organizationId=1")
            }
        }
    }

    "DeleteTemplate" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) },
                successStatus = HttpStatusCode.NotFound
            ) {
                delete("/admin/plugins/$pluginType/$pluginId/templates/$templateName")
            }
        }
    }

    "DisableGlobalTemplate" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) },
                successStatus = HttpStatusCode.NotFound
            ) {
                post("/admin/plugins/$pluginType/$pluginId/templates/$templateName/disableGlobal")
            }
        }
    }

    "DisablePlugin" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) },
                successStatus = HttpStatusCode.Accepted
            ) {
                post("/admin/plugins/$pluginType/$pluginId/disable")
            }
        }
    }

    "EnableGlobalTemplate" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) },
                successStatus = HttpStatusCode.NotFound
            ) {
                post("/admin/plugins/$pluginType/$pluginId/templates/$templateName/enableGlobal")
            }
        }
    }

    "EnablePlugin" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) },
                successStatus = HttpStatusCode.NotModified
            ) {
                post("/admin/plugins/$pluginType/$pluginId/enable")
            }
        }
    }

    "GetInstalledPlugins" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) }
            ) {
                get("/admin/plugins")
            }
        }
    }

    "GetTemplate" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) },
                successStatus = HttpStatusCode.NotFound
            ) {
                get("/admin/plugins/$pluginType/$pluginId/templates/$templateName")
            }
        }
    }

    "GetTemplates" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) },
                successStatus = HttpStatusCode.OK
            ) {
                get("/admin/plugins/$pluginType/$pluginId/templates")
            }
        }
    }

    "RemoveTemplateFromOrganization" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) },
                successStatus = HttpStatusCode.NotFound
            ) {
                post(
                    "/admin/plugins/$pluginType/$pluginId/templates/$templateName" +
                            "/removeFromOrganization?organizationId=1"
                )
            }
        }
    }

    "UpdateTemplateOptions" should {
        "require the superuser role" {
            requestShouldRequireSuperuser(
                routes = { pluginManagerRoutes(pluginEventStore, pluginService, pluginTemplateService) },
                successStatus = HttpStatusCode.OK
            ) {
                post("/admin/plugins/$pluginType/$pluginId/templates/$templateName") {
                    setBody(emptyList<PluginOptionTemplate>())
                }
            }
        }
    }
})
