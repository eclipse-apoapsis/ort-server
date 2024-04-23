/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.api.v1.model

import kotlinx.serialization.Serializable

/**
 * Configuration for Jira notifications.
 * Notifications are typically sent after an [OrtRun] using a built-in Jira REST client.
 */
@Serializable
data class JiraNotificationConfiguration(
    val jiraRestClientConfiguration: JiraRestClientConfiguration? = null
)

/**
 *  Configuration for a Jira REST client interacting with a Jira server after an [OrtRun].
 */
@Serializable
data class JiraRestClientConfiguration(
    /**
     * The URL of the Jira server, e.g. "https://jira.example.com".
     */
    val serverUrl: String,

    /**
     * The username to authenticate with the Jira server.
     */
    val username: String,

    /**
     * The password to authenticate with the Jira server.
     */
    val password: String
)
