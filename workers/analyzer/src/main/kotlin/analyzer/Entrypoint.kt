/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.workers.analyzer

import com.typesafe.config.ConfigFactory

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("org.ossreviewtoolkit.server.workers.analyzer.EntrypointKt")

/**
 * This is the entry point of the Analyzer worker. It calls the Analyzer from ORT programmatically by
 * interfacing on its APIs.
 */
fun main() {
    // Reading environment variables, which could be set e.g. in a docker compose file. Otherwise, use default
    // values. This is only an experimental approach to get access to ORT server specific environment variables,
    // which could be improved by using a configuration file.
    val host = System.getenv("ORT_SERVER_URL") ?: "http://localhost:8080"
    val user = System.getenv("ORT_SERVER_USER") ?: "admin"
    val password = System.getenv("ORT_SERVER_PASSWORD") ?: "admin"
    val authUrl = System.getenv("ORT_SERVER_AUTH_URL")
        ?: "http://localhost:8081/realms/master/protocol/openid-connect/token"
    val clientId = System.getenv("ORT_SERVER_CLIENT_ID") ?: "ort-server"
    logger.info("ORT server base URL: $host")
    logger.info("ORT server user: $user")
    logger.info("ORT server authentication URL: $authUrl")
    logger.info("ORT server client ID: $clientId")

    val config = ConfigFactory.load()

    ServerClient.create(host, user, password, clientId, authUrl).use {
        AnalyzerWorker(config, it).start()
    }
}
