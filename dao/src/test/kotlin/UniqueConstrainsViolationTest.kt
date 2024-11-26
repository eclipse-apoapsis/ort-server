/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.dao

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly

import io.mockk.mockk

import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger

import org.eclipse.apoapsis.ortserver.dao.tables.shared.RemoteArtifactDao
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.model.runs.RemoteArtifact

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database

/**
 * A test class to test whether entities can be created in parallel and unique constraints violations are handled
 * properly.
 */
class UniqueConstrainsViolationTest : StringSpec() {
    private val dbExtension = extension(DatabaseTestExtension())

    init {
        "An exception about a unique constraint violation is handled by repeating the transaction" {
            val callCount = AtomicInteger()

            // In order to simulate the situation in worker pods, the creation of entities has to happen in
            // nested query calls. A worker runner typically starts a query/transaction and invokes
            // OrtRunService. The service calls repositories, which again invoke a query function.
            dbExtension.db.dbQuery {
                runCreateArtifactTest(db, callCount)
            }

            val artifacts = dbExtension.db.dbQuery {
                RemoteArtifactDao.all().toList().map(RemoteArtifactDao::mapToModel)
            }
            artifacts shouldContainExactly listOf(testArtifact)
        }
    }
}

/** An artifact for which an entity is to be created in tests. */
private val testArtifact = RemoteArtifact(
    url = "https://example.com/artifact",
    hashValue = "1234567890",
    hashAlgorithm = "SHA-256"
)

/**
 * Create an artifact in the given [database][db] in a new query to simulate the invocation of a repository. Fail the
 * operation on the first call (determined by the given [counter][callCount]) with a unique constraint violation
 * exception to test whether this error condition is handled properly.
 */
private fun runCreateArtifactTest(db: Database, callCount: AtomicInteger) {
    db.blockingQuery {
        if (callCount.andIncrement == 0) {
            // Fail the first call to simulate a unique constraint violation.
            val sqlException = SQLException(
                "Unique constraint violation",
                PostgresErrorCodes.UNIQUE_CONSTRAINT_VIOLATION.value
            )
            throw ExposedSQLException(sqlException, emptyList(), mockk())
        }

        createArtifact(db)
    }
}

/**
 * Create the test artifact in the given [database][db].
 */
private fun createArtifact(db: Database) {
    db.blockingQuery {
        RemoteArtifactDao.getOrPut(testArtifact)
    }
}
