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

package org.eclipse.apoapsis.ortserver.client.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

import org.eclipse.apoapsis.ortserver.api.v1.model.OidcConfig

/**
 * A client for the Authentication API.
 */
class AuthenticationApi(
    private val client: HttpClient
) {
    /**
     * Get CLI specific [OidcConfig].
     */
    suspend fun getCliOidcConfig() = client.get("api/v1/auth/oidc-config/cli").body<OidcConfig>()
}
