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

package org.eclipse.apoapsis.ortserver.components.pluginmanager

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension

import org.ossreviewtoolkit.plugins.advisors.vulnerablecode.VulnerableCodeFactory

class PluginServiceTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var pluginEventStore: PluginEventStore
    lateinit var pluginService: PluginService

    val pluginType = PluginType.ADVISOR
    val pluginId = VulnerableCodeFactory.descriptor.id

    beforeEach {
        pluginEventStore = PluginEventStore(dbExtension.db)
        pluginService = PluginService(dbExtension.db)
    }

    "getAvailability" should {
        "return ENABLED if the plugin was never disabled" {
            pluginService.getAvailability(pluginType, pluginId) shouldBe PluginAvailability.ENABLED
        }

        "return ENABLED if the plugin was disabled and enabled again" {
            pluginEventStore.appendEvent(PluginEvent(pluginType, pluginId, 1, PluginDisabled, "user"))
            pluginEventStore.appendEvent(PluginEvent(pluginType, pluginId, 2, PluginEnabled, "user"))

            pluginService.getAvailability(pluginType, pluginId) shouldBe PluginAvailability.ENABLED
        }

        "normalize the plugin ID" {
            pluginService.getAvailability(pluginType, pluginId.lowercase()) shouldBe PluginAvailability.ENABLED
            pluginService.getAvailability(pluginType, pluginId.uppercase()) shouldBe PluginAvailability.ENABLED
        }

        "return DISABLED if the plugin was disabled" {
            pluginEventStore.appendEvent(PluginEvent(pluginType, pluginId, 1, PluginDisabled, "user"))

            pluginService.getAvailability(pluginType, pluginId) shouldBe PluginAvailability.DISABLED
        }

        "return RESTRICTED if the plugin was restricted" {
            pluginEventStore.appendEvent(PluginEvent(pluginType, pluginId, 1, PluginRestricted, "user"))

            pluginService.getAvailability(pluginType, pluginId) shouldBe PluginAvailability.RESTRICTED
        }
    }

    "isInstalled" should {
        "return true if the plugin is installed" {
            pluginService.isInstalled(pluginType, pluginId) shouldBe true
        }

        "normalize the plugin ID" {
            pluginService.isInstalled(pluginType, pluginId.lowercase()) shouldBe true
            pluginService.isInstalled(pluginType, pluginId.uppercase()) shouldBe true
        }

        "return false if the plugin is not installed" {
            pluginService.isInstalled(pluginType, "unknown") shouldBe false
        }
    }

    "getPlugins" should {
        "return all plugin types" {
            val plugins = pluginService.getPlugins()

            PluginType.entries.forAll { pluginType ->
                plugins.filter { it.type == pluginType } shouldNot beEmpty()
            }
        }

        "return the availability of a plugin" {
            pluginService.getPlugins().single { it.type == pluginType && it.id == pluginId }
                .availability shouldBe PluginAvailability.ENABLED

            pluginEventStore.appendEvent(PluginEvent(pluginType, pluginId, 1, PluginRestricted, "user"))

            pluginService.getPlugins().single { it.type == pluginType && it.id == pluginId }
                .availability shouldBe PluginAvailability.RESTRICTED

            pluginEventStore.appendEvent(PluginEvent(pluginType, pluginId, 2, PluginDisabled, "user"))

            pluginService.getPlugins().single { it.type == pluginType && it.id == pluginId }
                .availability shouldBe PluginAvailability.DISABLED
        }
    }
})
