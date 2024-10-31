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

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.utils.config.getIntOrDefault
import org.eclipse.apoapsis.ortserver.utils.config.getIntOrNull
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

/**
 * A data class storing configuration options for accessing a Grafana Loki instance.
 *
 * Note that according to [Grafana docs](https://grafana.com/docs/loki/latest/operations/authentication/), Loki does not
 * support an authentication by itself, but requires a reverse proxy for this purpose. This means that in theory
 * multiple different options for authentication could be supported. This implementation is currently limited to an
 * optional basic authentication: If both a username and password are configured, the corresponding authorization header
 * is set; otherwise, it is skipped.
 */
data class LokiConfig(
    /**
     * The URL of the Grafana Loki server to interact with. Note that this is the root URL; the path to the endpoint
     * ("/loki/api/v1/...") is appended automatically.
     */
    val serverUrl: String,

    /**
     * The namespace for which logs are to be retrieved. This has to be added to query strings sent to Loki.
     */
    val namespace: String,

    /**
     * A limit to be passed as parameter when calling the query endpoint. The Loki API expects such a limit parameter
     * or uses a (rather small) limit as default.
     */
    val limit: Int,

    /** An optional username for basic auth. */
    val username: String?,

    /** An optional password for basic auth. */
    val password: String?,

    /**
     * An optional tenant ID in case Grafana Loki runs in multi-tenant mode. If defined, requests sent to the server
     * will contain an additional header `X-Scope-OrgID` with this value.
     * See https://grafana.com/docs/loki/latest/operations/authentication/.
     */
    val tenantId: String? = null,

    /**
     * An optional timeout for requests sent to the Loki REST API in seconds. If this is unspecified, the default
     * timeout from the Ktor HTTP client is used.
     */
    val timeoutSec: Int? = null
) {
    companion object {
        /** The configuration property that defines the root URL of the Loki server. */
        private const val SERVER_URL_PROPERTY = "lokiServerUrl"

        /** The configuration property that defines the namespace from which logs are to be gathered. */
        private const val NAMESPACE_PROPERTY = "lokiNamespace"

        /** The configuration property that defines the username for basic authentication. */
        private const val USERNAME_PROPERTY = "lokiUsername"

        /** The configuration property that defines the password for basic authentication. */
        private const val PASSWORD_PROPERTY = "lokiPassword"

        /** The configuration property that defines a limit for query results. */
        private const val LIMIT_PROPERTY = "lokiQueryLimit"

        /** The configuration property that defines the tenant ID if in multi-tenant mode. */
        private const val TENANT_PROPERTY = "lokiTenantId"

        /** The configuration property that defines a timeout for queries against the Loki REST API in seconds. */
        private const val TIMEOUT_SEC_PROPERTY = "lokiTimeoutSec"

        /** The default limit to be passed to the query endpoint. */
        private const val DEFAULT_LIMIT = 1000

        /**
         * Return a new instance of [LokiConfig] that has been initialized from the passed in [configManager].
         */
        fun create(configManager: ConfigManager): LokiConfig {
            val username = configManager.getStringOrNull(USERNAME_PROPERTY)
            val password = if (username == null) {
                null
            } else {
                configManager.getSecret(Path(PASSWORD_PROPERTY))
            }

            return LokiConfig(
                configManager.getString(SERVER_URL_PROPERTY),
                configManager.getString(NAMESPACE_PROPERTY),
                configManager.getIntOrDefault(LIMIT_PROPERTY, DEFAULT_LIMIT),
                username,
                password,
                configManager.getStringOrNull(TENANT_PROPERTY),
                configManager.getIntOrNull(TIMEOUT_SEC_PROPERTY)
            )
        }
    }
}
