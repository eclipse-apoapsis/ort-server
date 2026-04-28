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

import com.github.michaelbull.result.Ok

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import io.mockk.every
import io.mockk.mockk

import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginAvailability
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginDescriptor
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplate
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginTemplateService
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginType
import org.eclipse.apoapsis.ortserver.model.OrganizationId

class UtilsTest : WordSpec({
    "getDefaultPackageManagers()" should {
        "return all enabled and restricted package managers available for an organization that are ORT defaults" {
            val pluginService = mockk<PluginService> {
                every { getPlugins() } returns listOf(
                    PluginDescriptor(
                        "Maven",
                        PluginType.PACKAGE_MANAGER,
                        "Maven",
                        "description",
                        availability = PluginAvailability.DISABLED
                    ),
                    PluginDescriptor(
                        "NPM",
                        PluginType.PACKAGE_MANAGER,
                        "NPM",
                        "description",
                        availability = PluginAvailability.RESTRICTED
                    ),
                    PluginDescriptor(
                        "PNPM",
                        PluginType.PACKAGE_MANAGER,
                        "PNPM",
                        "description",
                        availability = PluginAvailability.ENABLED
                    ),
                    PluginDescriptor(
                        "Yarn2",
                        PluginType.PACKAGE_MANAGER,
                        "Yarn2",
                        "description",
                        availability = PluginAvailability.RESTRICTED
                    ),
                    PluginDescriptor(
                        "OSV",
                        PluginType.ADVISOR,
                        "OSV",
                        "description",
                        availability = PluginAvailability.ENABLED
                    )
                )
            }

            val pluginTemplateService = mockk<PluginTemplateService> {
                every { getTemplateForOrganization(any(), any(), any()) } answers {
                    // Return a template for NPM but not for Yarn2 to validate that only restricted plugins with a
                    // template are added to the defaults.
                    if (secondArg<String>() == "NPM") {
                        Ok(
                            PluginTemplate(
                                name = "PNPM",
                                pluginType = PluginType.PACKAGE_MANAGER,
                                pluginId = "template content",
                                options = emptyList(),
                                isGlobal = false,
                                organizationIds = listOf(1)
                            )
                        )
                    } else {
                        Ok(null)
                    }
                }
            }

            val defaultPackageManagers =
                getDefaultPackageManagers(pluginService, pluginTemplateService, OrganizationId(1))

            defaultPackageManagers should containExactlyInAnyOrder("NPM", "PNPM")
        }
    }
})
