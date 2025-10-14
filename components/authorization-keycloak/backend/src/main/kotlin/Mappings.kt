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

package org.eclipse.apoapsis.ortserver.components.authorization.keycloak

import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.api.OrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.api.ProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.api.RepositoryRole
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.OrganizationRole as ModelOrganizationRole
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.ProductRole as ModelProductRole
import org.eclipse.apoapsis.ortserver.components.authorization.keycloak.roles.RepositoryRole as ModelRepositoryRole

fun OrganizationRole.mapToModel() = when (this) {
    OrganizationRole.READER -> ModelOrganizationRole.READER
    OrganizationRole.WRITER -> ModelOrganizationRole.WRITER
    OrganizationRole.ADMIN -> ModelOrganizationRole.ADMIN
}

fun ProductRole.mapToModel() = when (this) {
    ProductRole.READER -> ModelProductRole.READER
    ProductRole.WRITER -> ModelProductRole.WRITER
    ProductRole.ADMIN -> ModelProductRole.ADMIN
}

fun RepositoryRole.mapToModel() = when (this) {
    RepositoryRole.READER -> ModelRepositoryRole.READER
    RepositoryRole.WRITER -> ModelRepositoryRole.WRITER
    RepositoryRole.ADMIN -> ModelRepositoryRole.ADMIN
}
