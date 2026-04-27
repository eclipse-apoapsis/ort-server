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

package org.eclipse.apoapsis.ortserver.logaccess.elasticsearch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A data class that represents the relevant parts of a response sent by Elasticsearch for a search request.
 */
@Serializable
internal data class ElasticsearchResponse(val hits: ElasticsearchHits)

/**
 * A data class that represents the hits section of an Elasticsearch search response.
 */
@Serializable
internal data class ElasticsearchHits(val hits: List<ElasticsearchHit>)

/**
 * A data class that represents a single Elasticsearch search hit.
 */
@Serializable
internal data class ElasticsearchHit(
    /** The document ID assigned by Elasticsearch, used only for diagnostics. */
    @SerialName("_id")
    val id: String? = null,

    /** The source document with the log fields used by this provider. */
    @SerialName("_source")
    val source: ElasticsearchSource = ElasticsearchSource(),

    /** The sort values returned by Elasticsearch and used for `search_after` pagination. */
    val sort: List<JsonElement> = emptyList()
)

/**
 * A data class that represents the source fields read from an Elasticsearch hit.
 */
@Serializable
internal data class ElasticsearchSource(
    /** The rendered log statement as it was written by the application. */
    val message: String? = null
)
