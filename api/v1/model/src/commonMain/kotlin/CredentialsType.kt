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

package org.eclipse.apoapsis.ortserver.api.v1.model

/**
 * An enumeration class defining the supported credential types that are available for infrastructure services.
 * Depending on the type(s) set, the credentials of the service are added to different configuration files. Note that
 * infrastructure services can be assigned multiple credential types. It is also possible that they have no type;
 * then they are ignored when generating configuration files for credentials.
 */
enum class CredentialsType {
    /**
     * Credentials type indicating that the credentials of the service should be added to the _.netrc_ file. This is
     * in most cases the desired option, since it allows access to the service from most external tools.
     */
    NETRC_FILE,

    /**
     * Credentials type indicating that the credentials of the service should be added to the _.git-credentials_ file.
     * For normal cases, it should not be necessary to use this type, since Git should be able to obtain the
     * credentials from the _.netrc_ file. There are, however, rare cases when Git is not able to authenticate against
     * a repository based on the information in the _.netrc_ file. This was observed for instance with repositories
     * hosted on Microsoft Azure DevOps servers.
     */
    GIT_CREDENTIALS_FILE
}
