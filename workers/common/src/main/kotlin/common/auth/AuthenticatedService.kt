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
 * A data class describing a service with credentials for authentication.
 *
 * During the execution of a worker, instances of this class are created to access the infrastructure services
 * relevant for the current ORT run.
 */
data class AuthenticatedService(
    /** The name of the corresponding infrastructure service. */
    val name: String,

    /** The URI of the service. */
    val uri: String,

    /** The username for authentication. */
    val username: String,

    /** The password for authentication. */
    val password: String
)
