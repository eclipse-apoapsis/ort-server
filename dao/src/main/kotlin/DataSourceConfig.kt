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

package org.eclipse.apoapsis.ortserver.dao

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.utils.config.getStringOrNull

/**
 * A holder for the database configuration, used to set up the
 * [Hikari connection pool](https://github.com/brettwooldridge/HikariCP).
 */
data class DataSourceConfig(
    /** The host of the database, for example, 'localhost'. */
    val host: String,

    /** The port of the database, for example, '5432'. */
    val port: Int,

    /** The name of the database, for example, 'postgres'. */
    val name: String,

    /** The schema to use, for example, 'public'. */
    val schema: String,

    /** The username used for connecting to the database. */
    val username: String,

    /** The password used for connecting to the database. */
    val password: String,

    /**
     * The maximum number of milliseconds to wait for connections from the pool. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    val connectionTimeout: Long,

    /**
     * The maximum number of milliseconds a connection is allowed to sit idle in the pool. This requires that
     * [minimumIdle] is set to a value lower than [maximumPoolSize]. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    val idleTimeout: Long,

    /**
     * The frequency in milliseconds that the pool will attempt to keep an idle connection alive. Must be set to a value
     * lower than [maxLifetime]. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    val keepaliveTime: Long,

    /**
     * The maximum lifetime of a connection in milliseconds. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    val maxLifetime: Long,

    /**
     * The maximum size of the connection pool. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    val maximumPoolSize: Int,

    /**
     * The minimum number of idle connections that the pool tries to maintain. For details see the
     * [Hikari documentation](https://github.com/brettwooldridge/HikariCP#frequently-used).
     */
    val minimumIdle: Int,

    /**
     * The SSL mode to use. For available modes see the
     * [PostgreSQL documentation](https://www.postgresql.org/docs/current/libpq-ssl.html#LIBPQ-SSL-PROTECTION).
     */
    val sslMode: String,

    /**
     * The location of the file containing the SSL certificates. For details see the
     * [PostgreSQL documentation](https://www.postgresql.org/docs/current/libpq-ssl.html#LIBPQ-SSL-CLIENTCERT).
     */
    val sslCert: String?,

    /**
     * The location of the file containing the SSL keys. For details see the
     * [PostgreSQL documentation](https://www.postgresql.org/docs/current/libpq-ssl.html#LIBPQ-SSL-CLIENTCERT).
     */
    val sslKey: String?,

    /**
     * The location of the root certificate file. For details see the
     * [PostgreSQL documentation](https://www.postgresql.org/docs/current/libpq-ssl.html#LIBQ-SSL-CERTIFICATES).
     */
    val sslRootCert: String?,

    /**
     * An optional SQL statement that is executed when a new database connection is created. This can be used to
     * set some defaults, for instance, the schema search path.
     */
    val initSqlStatement: String?
) {
    companion object {
        /**
         * Create a [DataSourceConfig] object from the provided [config].
         */
        fun create(config: ConfigManager) = DataSourceConfig(
            host = config.getString("database.host"),
            port = config.getInt("database.port"),
            name = config.getString("database.name"),
            schema = config.getString("database.schema"),
            username = config.getSecret(Path("database.username")),
            password = config.getSecret(Path("database.password")),

            connectionTimeout = config.getLong("database.connectionTimeout"),
            idleTimeout = config.getLong("database.idleTimeout"),
            keepaliveTime = config.getLong("database.keepaliveTime"),
            maxLifetime = config.getLong("database.maxLifetime"),
            maximumPoolSize = config.getInt("database.maximumPoolSize"),
            minimumIdle = config.getInt("database.minimumIdle"),

            sslMode = config.getString("database.sslMode"),
            sslCert = config.getStringOrNull("database.sslCert"),
            sslKey = config.getStringOrNull("database.sslKey"),
            sslRootCert = config.getStringOrNull("database.sslRootCert"),
            initSqlStatement = config.getStringOrNull("database.initSqlStatement")
        )
    }
}
