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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.eclipse.apoapsis.ortserver.dao.UniqueConstraintException
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.dao.transaction
import org.eclipse.apoapsis.ortserver.model.OrganizationId

import org.jetbrains.exposed.v1.jdbc.selectAll

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

    suspend fun readModel() = dbExtension.db.transaction {
        DefaultPluginsReadModel.selectAll().map {
            Triple(
                it[DefaultPluginsReadModel.organizationId],
                it[DefaultPluginsReadModel.category],
                it[DefaultPluginsReadModel.pluginIds]
            )
        }
    }

    "getDefaultPlugins" should {
        "return a state without plugins if no events exist" {
            val state = eventStore.getDefaultPlugins(null, category)

            state.pluginIds should beNull()
            state.version shouldBe 0
        }

        "apply all events in order" {
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("a", "b")), "u"))
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 2, DefaultPluginsSet(listOf("c", "a")), "u"))

            val state = eventStore.getDefaultPlugins(null, category)

            state.pluginIds shouldBe listOf("c", "a")
            state.version shouldBe 2
        }

        "return no plugins after a reset" {
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("a")), "u"))
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 2, DefaultPluginsReset, "u"))

            val state = eventStore.getDefaultPlugins(null, category)

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

            eventStore.getDefaultPlugins(null, category).pluginIds shouldBe listOf("g")
            eventStore.getDefaultPlugins(organizationId, category).pluginIds shouldBe listOf("o")
            eventStore.getDefaultPlugins(null, DefaultPluginsCategory.ADVISOR).pluginIds shouldBe listOf("adv")
            eventStore.getDefaultPlugins(organizationId, DefaultPluginsCategory.ADVISOR).pluginIds should beNull()
        }
    }

    "appendEvent" should {
        "reject events with a duplicate version" {
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("a")), "u"))

            shouldThrow<UniqueConstraintException> {
                eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("b")), "u"))
            }
        }

        "update the read model" {
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("a", "b")), "u"))
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 2, DefaultPluginsSet(listOf("b", "a")), "u"))
            eventStore.appendEvent(
                DefaultPluginsEvent(organizationId, category, 1, DefaultPluginsSet(listOf("o")), "u")
            )

            readModel() should containExactlyInAnyOrder(
                Triple(null, category, listOf("b", "a")),
                Triple(organizationId.value, category, listOf("o"))
            )
        }

        "remove the entry from the read model on reset" {
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 1, DefaultPluginsSet(listOf("a")), "u"))
            eventStore.appendEvent(DefaultPluginsEvent(null, category, 2, DefaultPluginsReset, "u"))

            readModel() should beEmpty()
        }
    }
})
