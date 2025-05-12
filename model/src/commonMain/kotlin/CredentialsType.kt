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

package org.eclipse.apoapsis.ortserver.model

/**
 * An enumeration class defining the supported credential types that are available for infrastructure services.
 * Depending on the type(s) set, the credentials of the service are added to different configuration files. Note that
 * infrastructure services can be assigned multiple credential types. It is also possible that they have no type;
 * then they are ignored when generating configuration files for credentials. They are, however, always considered by
 * the global authenticator of the JVM, unless the [NO_AUTHENTICATION] flag is set.
 */
enum class CredentialsType {
    /**
     * Credentials type indicating that the credentials of the service should be added to the _.netrc_ file. This is
     * needed if the underlying service URL needs to be accessed from external tools (except for Git) that rely on the
     * mechanism of the _.netrc_ file.
     */
    NETRC_FILE,

    /**
     * Credentials type indicating that the credentials of the service should be added to the _.git-credentials_ file.
     * This flag should typically be set for services representing Git repositories to make sure that the Git CLI can
     * obtain their credentials.
     */
    GIT_CREDENTIALS_FILE,

    /**
     * Credentials type indicating that this service does not require any authentication. This type is evaluated by
     * the authenticator; if it is present, the authenticator will not return any authentication information. This is
     * useful for instance for public repositories or services like Maven Central.
     */
    NO_AUTHENTICATION
}
