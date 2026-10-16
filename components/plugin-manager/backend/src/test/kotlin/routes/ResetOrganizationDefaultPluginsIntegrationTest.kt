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

package org.eclipse.apoapsis.ortserver.components.pluginmanager.routes

import com.github.michaelbull.result.get

import io.kotest.assertions.ktor.client.shouldHaveStatus
import io.kotest.matchers.shouldBe

import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPlugins
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsCategory.ADVISOR
import org.eclipse.apoapsis.ortserver.components.pluginmanager.DefaultPluginsSource
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginManagerIntegrationTest
import org.eclipse.apoapsis.ortserver.model.OrganizationId

import org.ossreviewtoolkit.plugins.advisors.ossindex.OssIndexFactory
import org.ossreviewtoolkit.plugins.advisors.osv.OsvFactory

class ResetOrganizationDefaultPluginsIntegrationTest : PluginManagerIntegrationTest({
    var organizationId = OrganizationId(-1L)

    beforeEach {
        organizationId = OrganizationId(dbExtension.fixtures.organization.id)
    }

    "ResetOrganizationDefaultPlugins" should {
        "reset the organization defaults to the global defaults" {
            pluginManagerTestApplication { client ->
                val globalPluginIds = listOf(OssIndexFactory.descriptor.id)
                defaultPluginsService.setDefaultPlugins(null, ADVISOR, globalPluginIds, "test-user")
                defaultPluginsService.setDefaultPlugins(
                    organizationId,
                    ADVISOR,
                    listOf(OsvFactory.descriptor.id),
                    "test-user"
                )

                client.delete(
                    "/organizations/${organizationId.value}/plugins/defaults/$ADVISOR"
                ) shouldHaveStatus HttpStatusCode.NoContent

                defaultPluginsService.getDefaultPlugins(organizationId, ADVISOR).get() shouldBe
                        DefaultPlugins(ADVISOR, globalPluginIds, DefaultPluginsSource.GLOBAL)
            }
        }

        "succeed if no organization defaults are configured" {
            pluginManagerTestApplication { client ->
                client.delete(
                    "/organizations/${organizationId.value}/plugins/defaults/$ADVISOR"
                ) shouldHaveStatus HttpStatusCode.NoContent
            }
        }

        "return NotFound if the organization does not exist" {
            pluginManagerTestApplication { client ->
                client.delete(
                    "/organizations/999999/plugins/defaults/$ADVISOR"
                ) shouldHaveStatus HttpStatusCode.NotFound
            }
        }
    }
})
