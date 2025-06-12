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

package org.eclipse.apoapsis.ortserver.clients.keycloak

/**
 * A data class representing the configuration of the Keycloak client.
 *
 * The major part of the properties defined here determine the way to obtain access tokens. There are two options:
 * - If an [apiUser] is specified, the grant type "password" is used assuming a public client.
 * - If [apiUser] is empty, tokens are obtained via the grant type "client credentials". Then the [apiSecret] is
 *   interpreted as the secret of a confidential client.
 */
data class KeycloakClientConfiguration(
    val apiUrl: String,
    val clientId: String,
    val accessTokenUrl: String,
    val apiUser: String,
    val apiSecret: String,

    /**
     * The (external) ID of the client to be manipulated by the Keycloak client. The roles of this client will be
     * updated.
     */
    val subjectClientId: String,

    /**
     * The size of the chunks in which data is fetched from Keycloak. This is used to avoid timeouts when fetching
     * large lists of results (i.e. groups). In the future this param can be parameterized just by adding an entry
     * to the [ConfigManager.createKeycloakClientConfiguration] method.
     */
    val dataGetChunkSize: Int = 5000
)
