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

package org.eclipse.apoapsis.ortserver.dao

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldMatchInOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldMatch

import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationDao
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.OrganizationsTable
import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension
import org.eclipse.apoapsis.ortserver.utils.test.Integration

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.EntityLifecycleInterceptor
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

import org.slf4j.LoggerFactory

class SqlQueryTraceLoggerTest : WordSpec({
    tags(Integration)

    val dbExtension = extension(DatabaseTestExtension())

    val logger = LoggerFactory.getLogger("Exposed") as Logger
    val listAppender = ListAppender<ILoggingEvent>().apply { start() }
    lateinit var originalLevel: Level

    beforeSpec {
        originalLevel = logger.level
        logger.addAppender(listAppender)
    }

    afterSpec {
        logger.detachAppender(listAppender)
        logger.level = originalLevel
    }

    beforeEach {
        listAppender.list.clear()
    }

    fun queryOrganizations() {
        transaction(dbExtension.db) {
            OrganizationsTable.selectAll().where { OrganizationsTable.name eq "test" }.toList()
        }
    }

    /** Return the events logged for statements on the organizations table. */
    fun loggedStatements() = listAppender.list.filter { "organizations" in it.formattedMessage }

    "SqlQueryTraceLogger" should {
        "log statements with their duration and arguments at TRACE level" {
            logger.level = Level.TRACE

            queryOrganizations()

            val event = loggedStatements().single()
            event.level shouldBe Level.TRACE
            event.loggerName shouldBe "Exposed"
            event.formattedMessage shouldMatch
                Regex("""\[\d+\.\d{2}ms] SELECT .* FROM organizations WHERE organizations\."name" = 'test'""")
        }

        "log statements that are executed while another statement is executed" {
            logger.level = Level.TRACE

            transaction(dbExtension.db) {
                // Exposed DAO flushes the insert of the new entity before executing the query.
                OrganizationDao.new { name = "flushed" }
                OrganizationsTable.selectAll().where { OrganizationsTable.name eq "flushed" }.toList()
            }

            loggedStatements().map { it.formattedMessage } shouldMatchInOrder listOf(
                { it shouldMatch Regex("""\[\d+\.\d{2}ms] INSERT INTO organizations .*'flushed'.*""") },
                { it shouldMatch Regex("""\[\d+\.\d{2}ms] SELECT .* FROM organizations WHERE .*'flushed'""") }
            )
        }

        "be called after the global interceptors of Exposed" {
            JdbcTransaction.globalInterceptors.last() shouldBe SqlQueryTraceLogger
            JdbcTransaction.globalInterceptors.filterIsInstance<EntityLifecycleInterceptor>() shouldNot beEmpty()
        }

        "not log statements if TRACE level is disabled" {
            logger.level = Level.DEBUG

            queryOrganizations()

            loggedStatements() should beEmpty()
        }
    }
})
