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

import org.eclipse.apoapsis.ortserver.model.Secret

/**
 * Definition of a function that can resolve the value of a [Secret] used as a credential of an environment service.
 * The function takes a [Secret] as input and returns its value or throws an [IllegalArgumentException] if the secret
 * cannot be resolved. Note that the function cannot handle arbitrary secrets; it addresses secrets referenced by
 * infrastructure services that have been resolved when setting up the environment for a worker execution.
 */
internal typealias CredentialResolverFun = (Secret) -> String

/**
 * A constant for a [CredentialResolverFun] that always fails with an [IllegalArgumentException]. This can be used
 * if no authentication information is available yet.
 */
internal val undefinedCredentialResolver: CredentialResolverFun = {
    throw IllegalArgumentException("Secret '${it.path}' cannot be resolved.")
}

/**
 * Return a [CredentialResolverFun] that can resolve secrets based on the data stored in the provided
 * [authenticationInfo].
 */
internal fun credentialResolver(authenticationInfo: AuthenticationInfo): CredentialResolverFun =
    authenticationInfo::resolveSecret

/**
 * Resolve all the given [secrets] using the provided [resolverFun] and return a [Map] that assigns the secrets to
 * their values. This is a convenience function that can handle multiple secrets at once.
 */
internal fun resolveCredentials(resolverFun: CredentialResolverFun, vararg secrets: Secret): Map<Secret, String> =
    secrets.associateWith(resolverFun)
