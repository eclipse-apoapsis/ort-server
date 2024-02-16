/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.config

/**
 * A service provider interface for reading secrets from the configuration.
 *
 * Via this interface, workers can obtain secrets that are needed for communicating with external services. Such
 * secrets are different from the ones provided by the secrets abstraction. They are managed centrally by
 * administrators and not related to users and their repositories.
 *
 * The main purpose of this provider interface is to allow the integration with special secrets storage services
 * offered by the environment the server is running in.
 */
interface ConfigSecretProvider {
    /**
     * Return the value of the secret stored under the given [path]. Throw an exception if the path cannot be
     * resolved.
     */
    fun getSecret(path: Path): String
}
