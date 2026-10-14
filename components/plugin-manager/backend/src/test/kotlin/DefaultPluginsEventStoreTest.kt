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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.OrganizationId

class DefaultPluginsEventStoreTest : WordSpec({
    val dbExtension = extension(DatabaseTestExtension())

    lateinit var fixtures: Fixtures
    lateinit var eventStore: DefaultPluginsEventStore
    var organizationId = OrganizationId(0)

    val category = DefaultPluginsCategory.PACKAGE_CURATION_PROVIDER

    beforeEach {
        fixtures = dbExtension.fixtures
        eventStore = DefaultPluginsEventStore(dbExtension.db)
        organizationId = OrganizationId(fixtures.organization.id)
    }

    "loadState" should {
        "return a state without plugins if no events exist" {
            val state = eventStore.loadState(null, category)

            state.pluginIds should beNull()
            state.version shouldBe 0
        }

        "apply all events in order" {
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("a", "b")), "u"))
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 2, DefaultPluginsSet(listOf("c", "a")), "u"))

            val state = eventStore.loadState(null, category)

            state.pluginIds shouldBe listOf("c", "a")
            state.version shouldBe 2
        }

        "return no plugins after a reset" {
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("a")), "u"))
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 2, DefaultPluginsReset, "u"))

            val state = eventStore.loadState(null, category)

            state.pluginIds should beNull()
            state.version shouldBe 2
        }

        "separate global, organization, and plugin type scopes" {
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("g")), "u"))
            eventStore.appendEvent(
                DefaultPluginsEvent(organizationId, category, 1, DefaultPluginsSet(listOf("o")), "u")
            )
            eventStore.appendEvent(
                DefaultPluginsEvent(null, DefaultPluginsCategory.ADVISOR, 1, DefaultPluginsSet(listOf("adv")), "u")
            )

            eventStore.loadState(null, category).pluginIds shouldBe listOf("g")
            eventStore.loadState(organizationId, category).pluginIds shouldBe listOf("o")
            eventStore.loadState(null, DefaultPluginsCategory.ADVISOR).pluginIds shouldBe listOf("adv")
            eventStore.loadState(organizationId, DefaultPluginsCategory.ADVISOR).pluginIds should beNull()
        }
    }

    "appendEvent" should {
        "reject events with a duplicate version" {
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("a")), "u"))

            shouldThrow<UniqueConstraintException> {
                eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("b")), "u"))
            }
        }
    }

    "getConfiguredDefaults" should {
        "return the configured default plugins of the organization and the global default plugins" {
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("a", "b")), "u"))
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 2, DefaultPluginsSet(listOf("b", "a")), "u"))
            eventStore.appendEvent(
                DefaultPluginsEvent(organizationId, category, 1, DefaultPluginsSet(listOf("o")), "u")
            )
            eventStore.appendEvent(
                DefaultPluginsEvent(null, DefaultPluginsCategory.ADVISOR, 1, DefaultPluginsSet(listOf("adv")), "u")
            )

            val defaults = eventStore.getConfiguredDefaults(organizationId)

            defaults.organization shouldBe mapOf(category to listOf("o"))
            defaults.global shouldBe mapOf(
                category to listOf("b", "a"),
                DefaultPluginsCategory.ADVISOR to listOf("adv")
            )
        }

        "not return the default plugins of other organizations" {
            val otherOrganizationId = OrganizationId(fixtures.createOrganization(name = "other").id)
            eventStore.appendEvent(
                DefaultPluginsEvent(otherOrganizationId, category, 1, DefaultPluginsSet(listOf("o")), "u")
            )

            eventStore.getConfiguredDefaults(organizationId).organization shouldBe emptyMap()
            eventStore.getConfiguredDefaults(null).organization shouldBe emptyMap()
        }

        "not return reset default plugins" {
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("a")), "u"))
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 2, DefaultPluginsReset, "u"))

            eventStore.getConfiguredDefaults(null).global shouldBe emptyMap()
        }
    }
})
