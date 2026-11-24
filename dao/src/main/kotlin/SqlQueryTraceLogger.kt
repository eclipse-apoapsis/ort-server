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

package org.eclipse.apoapsis.ortserver.dao

import java.util.IdentityHashMap

import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

import org.jetbrains.exposed.v1.core.Key
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.exposedLogger
import org.jetbrains.exposed.v1.core.statements.GlobalStatementInterceptor
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi
import org.jetbrains.exposed.v1.core.statements.expandArgs
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

/**
 * A statement interceptor that logs executed SQL statements together with their duration at TRACE level.
 *
 * Exposed's [org.jetbrains.exposed.v1.core.SqlLogger] cannot be used for this, because loggers are called before
 * Exposed has measured the duration of a statement. Therefore, this interceptor measures the time between
 * [beforeExecution] and [afterExecution] itself. This covers preparing and executing the statement. With the default
 * fetch size of the PostgreSQL driver, this includes transferring all rows of a result, but not processing them in
 * Exposed, which happens later when the result is iterated.
 *
 * A statement can be executed while another one is being executed, because Exposed DAO flushes pending entity changes
 * in the `beforeExecution` callback of its own global interceptor. Therefore, the start times are stored per
 * [StatementContext], and this interceptor has to be registered with [register], so that it is called after the
 * interceptors of Exposed and the duration of a statement does not include the flushed statements.
 */
object SqlQueryTraceLogger : GlobalStatementInterceptor {
    /** The key to store the start times of the statements that are currently executed in a transaction. */
    private val startTimesKey = Key<MutableMap<StatementContext, Long>>()

    /**
     * Register this interceptor for all transactions. It is added after the global interceptors that Exposed loads
     * via the `ServiceLoader`, so that it is called after them. Calling this function multiple times has no effect.
     */
    fun register() {
        val globalInterceptors = JdbcTransaction.globalInterceptors

        synchronized(globalInterceptors) {
            if (this !in globalInterceptors) globalInterceptors += this
        }
    }

    override fun beforeExecution(transaction: Transaction, context: StatementContext) {
        if (!exposedLogger.isTraceEnabled) return

        val startTimes = transaction.getUserData(startTimesKey)
            ?: IdentityHashMap<StatementContext, Long>().also { transaction.putUserData(startTimesKey, it) }

        startTimes[context] = System.nanoTime()
    }

    override fun afterExecution(
        transaction: Transaction,
        contexts: List<StatementContext>,
        executedStatement: PreparedStatementApi
    ) {
        val startTimes = transaction.getUserData(startTimesKey) ?: return
        val startTime = contexts.mapNotNull { startTimes.remove(it) }.minOrNull() ?: return
        if (startTimes.isEmpty()) transaction.removeUserData(startTimesKey)

        val duration = (System.nanoTime() - startTime).nanoseconds.toString(DurationUnit.MILLISECONDS, decimals = 2)

        contexts.forEach { context ->
            exposedLogger.trace("[{}] {}", duration, context.expandArgs(transaction))
        }
    }
}
