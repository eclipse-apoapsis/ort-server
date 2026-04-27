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

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.utils.config.getIntOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getIntOrNull
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

/**
 * A data class storing configuration options for accessing an Elasticsearch instance.
 *
 * The provider expects logs to be indexed with the canonical field names documented in this module's README.
 *
 * Authentication is optional. If both Basic Auth credentials and an API key are configured, the API key takes
 * precedence and Basic Auth is ignored.
 */
data class ElasticsearchConfig(
    /** The base URL of the Elasticsearch instance. */
    val serverUrl: String,

    /** The index or index pattern queried by the provider, for example `ort-server-logs-*`. */
    val index: String,

    /**
     * The namespace used to restrict log queries, for example the Kubernetes namespace or the local `compose` label.
     */
    val namespace: String,

    /**
     * The maximum number of hits requested per search call.
     * If more hits are available, the provider continues with `search_after` pagination using the last hit's sort
     * values from the previous page.
     */
    val pageSize: Int,

    /** An optional username for Basic Authentication. Ignored when [apiKey] is configured. */
    val username: String?,

    /** An optional password for Basic Authentication. */
    val password: String?,

    /** An optional Elasticsearch API key. If present, it takes precedence over Basic Authentication. */
    val apiKey: String?,

    /** An optional timeout in seconds for requests sent to the Elasticsearch REST API. */
    val timeoutSec: Int? = null
) {
    companion object {
        /** The configuration property that defines the Elasticsearch server URL. */
        private const val SERVER_URL_PROPERTY = "elasticsearchServerUrl"

        /** The configuration property that defines the index or index pattern to query. */
        private const val INDEX_PROPERTY = "elasticsearchIndex"

        /** The configuration property that defines the namespace label used in search filters. */
        private const val NAMESPACE_PROPERTY = "elasticsearchNamespace"

        /** The configuration property that defines the page size used for search requests. */
        private const val PAGE_SIZE_PROPERTY = "elasticsearchPageSize"

        /** The configuration property that defines the username for Basic Authentication. */
        private const val USERNAME_PROPERTY = "elasticsearchUsername"

        /** The configuration property that defines the password for Basic Authentication. */
        private const val PASSWORD_PROPERTY = "elasticsearchPassword"

        /** The configuration property that defines the Elasticsearch API key. */
        private const val API_KEY_PROPERTY = "elasticsearchApiKey"

        /** The configuration property that defines a timeout for Elasticsearch requests in seconds. */
        private const val TIMEOUT_SEC_PROPERTY = "elasticsearchTimeoutSec"

        /** The default number of hits requested per page when no explicit page size is configured. */
        private const val DEFAULT_PAGE_SIZE = 1000

        /**
         * Return a new instance of [ElasticsearchConfig] initialized from the passed in [configManager].
         */
        fun create(configManager: ConfigManager): ElasticsearchConfig {
            val username = configManager.getStringOrNull(USERNAME_PROPERTY)?.ifBlank { null }
            val apiKey = configManager.getStringOrNull(API_KEY_PROPERTY)?.ifBlank { null }?.let {
                configManager.getSecret(Path(API_KEY_PROPERTY))
            }
            val password = if (username == null || apiKey != null) {
                null
            } else {
                configManager.getSecret(Path(PASSWORD_PROPERTY))
            }

            return ElasticsearchConfig(
                serverUrl = configManager.getString(SERVER_URL_PROPERTY),
                index = configManager.getString(INDEX_PROPERTY),
                namespace = configManager.getString(NAMESPACE_PROPERTY),
                pageSize = configManager.getIntOrDefault(PAGE_SIZE_PROPERTY, DEFAULT_PAGE_SIZE),
                username = username,
                password = password,
                apiKey = apiKey,
                timeoutSec = configManager.getIntOrNull(TIMEOUT_SEC_PROPERTY)
            )
        }
    }
}
