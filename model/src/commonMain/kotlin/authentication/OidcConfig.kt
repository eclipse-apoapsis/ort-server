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

package org.eclipse.apoapsis.ortserver.model.authentication

/**
 * The configuration details of the OpenID Connect provider. This data can be used by REST clients to set up their
 * authentication logic.
 */
data class OidcConfig(
    /**
     * The URL to the OpenID Connect provider's authorization endpoint.
     */
    val accessTokenUrl: String,

    /**
     * The client ID configured in the OpenID Connect provider.
     */
    val clientId: String
)
