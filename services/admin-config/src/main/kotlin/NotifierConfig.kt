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

package org.eclipse.apoapsis.ortserver.services.config

/**
 *  Configuration for a Jira REST client interacting with a Jira server after an ORT run.
 */
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

/**
 * Configuration for a mail server to send mails after an ORT run.
 */
data class MailServerConfiguration(
    /**
     * The address of the outgoing SMTP server that will be used to send the message.
     */
    val hostName: String = "localhost",

    /**
     * The port used for the SMTP server.
     */
    val port: Int = 587,

    /**
     * The username to authenticate with the SMTP server.
     */
    val username: String = "",

    /**
     * The password to authenticate with the SMTP server.
     */
    val password: String = "",

    /**
     * Configuration if SSL/TLS encryption should be enabled with the SMTP server.
     */
    val useSsl: Boolean = true,

    /**
     * The 'from' field of the outgoing email.
     */
    val fromAddress: String
)

/**
 * A data class that represents the configuration of the Notifier worker.
 *
 * An instance of this class is part of the [AdminConfig]. It specifies the path to the notifier script and the
 * infrastructure to use for sending notifications.
 */
data class NotifierConfig(
    /** The path to the notifier script to use. */
    val notifierRules: String,

    /**
     * The configuration for the server for sending email notifications. If this is `null`, no email notifications will
     * be sent.
     */
    val mail: MailServerConfiguration? = null,

    /**
     * The configuration for Jira notifications. If this is `null`, no Jira notifications will be sent.
     */
    val jira: JiraRestClientConfiguration? = null,

    /**
     * A flag to disable sending mail notifications. This can be used to (temporarily) disable email notifications
     * without removing the configuration.
     */
    val disableMailNotifications: Boolean = false,

    /**
     * A flag to disable creating Jira tickets. This can be used to (temporarily) disable Jira notifications
     * without removing the configuration.
     */
    val disableJiraNotifications: Boolean = false
)
