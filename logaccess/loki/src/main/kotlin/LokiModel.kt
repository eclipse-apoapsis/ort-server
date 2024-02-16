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

package org.eclipse.apoapsis.ortserver.logaccess.loki

import kotlinx.serialization.Serializable

/**
 * A data class that represents the relevant parts of a response sent by Loki for a range query.
 */
@Serializable
internal data class LokiResponse(val data: ResponseData)

/**
 * A data class that represents the data part of a response sent by Loki for a range query.
 */
@Serializable
internal data class ResponseData(val result: List<StreamResult>)

/**
 * A data class that represents the relevant parts of a Loki response with stream results. For range queries, Loki
 * might return multiple streams, although the queries sent by this provider implementation should yield a single
 * stream result only. See https://grafana.com/docs/loki/latest/reference/api/#matrix-vector-and-streams.
 */
@Serializable
internal data class StreamResult(
    /**
     * The log data of the current stream. For each log statement, Loki returns an array with two elements: the first
     * element is a timestamp string, the second one is the actual log statement.
     */
    val values: List<List<String>>
)

/**
 * A data class that represents a single log statement. The lists with two elements used in the Loki model for this
 * purpose are converted into instances of this class.
 */
internal data class LogStatement(
    /**
     * The timestamp of this log statement. This is the string representation of the Unix Epoch time when the
     * statement was generated.
     */
    val timestamp: String,

    /** The actual log statement as it was written by the application. */
    val statement: String
)

/**
 * Return a list with all log statements contained in this [LokiResponse].
 */
internal fun LokiResponse.logStatements(): List<LogStatement> =
    data.result.flatMap(StreamResult::values).map { LogStatement(it[0], it[1]) }
