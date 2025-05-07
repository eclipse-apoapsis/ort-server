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

package org.eclipse.apoapsis.ortserver.workers.common.auth

/**
 * A data class defining an event that is triggered when [OrtServerAuthenticator] successfully authenticates a request.
 */
data class AuthenticationEvent(
    /** The name of the infrastructure service whose credentials are used for authentication. */
    val serviceName: String
)

/**
 * An interface for listeners that receive notifications about successful authentications performed by
 * [OrtServerAuthenticator].
 *
 * An implementation of this interface can be added to [OrtServerAuthenticator]. It is then always invoked when a
 * request is successfully authenticated using the credentials of a specific infrastructure service. The purpose
 * behind this interface is to have a mechanism to dynamically update the environment based on the infrastructure
 * services that are involved in the current operation. This is especially necessary when dealing with external tools
 * that rely on the content of the _.netrc_ file for authentication. Since this file is limited and allows only one
 * set of credentials per host, it cannot be generated beforehand based on all infrastructure services defined; this
 * would lead to conflicts if there are multiple services for the same host. Instead, the idea is to keep track of the
 * set of infrastructure services that are currently referenced and also update the content of the _.netrc_ file if
 * this changes. This is, of course, not a perfect solution. It requires that the [OrtServerAuthenticator] is always
 * called before an external tool is launched.
 */
interface AuthenticationListener {
    /**
     * Notify this listener about a successful authentication with the details provided by the given
     * [authenticationEvent].
     */
    fun onAuthentication(authenticationEvent: AuthenticationEvent)
}
