/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

@file:OptIn(ExperimentalCoroutinesApi::class)

package org.eclipse.apoapsis.ortserver.dao

import java.sql.SQLException

import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Enum for PostgreSQL exception states, see https://www.postgresql.org/docs/10/errcodes-appendix.html.
 */
enum class PostgresErrorCodes(val value: String) {
    UNIQUE_CONSTRAINT_VIOLATION("23505")
}

class UniqueConstraintException(msg: String, cause: Throwable? = null) :
    SQLException(msg, cause), CopyableThrowable<UniqueConstraintException> {
    override fun createCopy() = UniqueConstraintException(checkNotNull(message), this)
}

/**
 * An exception class indicating that invalid query parameters have been passed to a query function.
 */
class QueryParametersException(msg: String, cause: Throwable? = null) :
    RuntimeException(msg, cause), CopyableThrowable<QueryParametersException> {
    override fun createCopy() = QueryParametersException(checkNotNull(message), this)
}
