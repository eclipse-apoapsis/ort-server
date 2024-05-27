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

package org.eclipse.apoapsis.ortserver.clients.keycloak.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A data class representing information about the tokens received from the API client.
 */
@Serializable
internal data class TokenInfo(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshTokenRaw: String? = null
) {
    /**
     * Return a refresh token that is not *null*. For some grant types, Keycloak does not support refresh tokens.
     */
    val refreshToken: String = refreshTokenRaw.orEmpty()
}
