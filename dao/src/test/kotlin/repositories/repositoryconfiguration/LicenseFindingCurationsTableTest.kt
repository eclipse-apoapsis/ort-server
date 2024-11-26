/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao.repositories.repositoryconfiguration

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull

import org.eclipse.apoapsis.ortserver.dao.blockingQuery
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension

import org.jetbrains.exposed.sql.insertAndGetId

class LicenseFindingCurationsTableTest : StringSpec({
    val extension = extension(DatabaseTestExtension())

    "Invalid start lines should be handled" {
        extension.db.blockingQuery {
            val id = LicenseFindingCurationsTable.insertAndGetId {
                it[path] = "somePath"
                it[concludedLicense] = "BSD-3-Clause"
                it[reason] = "INCORRECT"
                it[comment] = "just for testing"
                it[startLines] = "47,invalid,start,lines,11"
            }

            val dao = LicenseFindingCurationDao.findById(id.value).shouldNotBeNull()

            dao.startLines shouldContainExactly listOf(47, 11)
        }
    }
})
