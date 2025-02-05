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

import org.jetbrains.exposed.sql.SqlLogger
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.statements.StatementContext
import org.jetbrains.exposed.sql.statements.expandArgs
import org.jetbrains.exposed.sql.transactions.TransactionManager

/**
 * SQL Queries logger that logs SQL queries at TRACE level.
 * As Exposed is logging SQL queries only on DEBUG level, using [org.jetbrains.exposed.sql.Slf4jSqlDebugLogger],
 * which is used as default in [org.jetbrains.exposed.sql.DatabaseConfig], to change the logging level to TRACE
 * this logger have to be used, and set as default logger.
 */
object SqlQueryTraceLogger : SqlLogger {
    /**
     * Logs a message containing the string representation of a complete SQL statement.
     *
     * **Note:** This is only logged if TRACE level is currently enabled.
     */
    override fun log(context: StatementContext, transaction: Transaction) {
        if (exposedLogger.isTraceEnabled) {
            exposedLogger.trace(context.expandArgs(TransactionManager.current()))
        }
    }
}
