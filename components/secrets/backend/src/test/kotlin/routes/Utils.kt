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

package org.eclipse.apoapsis.ortserver.components.secrets.routes

import org.eclipse.apoapsis.ortserver.model.OrganizationId
import org.eclipse.apoapsis.ortserver.model.ProductId
import org.eclipse.apoapsis.ortserver.model.RepositoryId
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository

fun SecretRepository.createOrganizationSecret(
    orgId: Long,
    path: String = "path",
    name: String = "name",
    description: String = "description"
) = create(path, name, description, OrganizationId(orgId))

fun SecretRepository.createProductSecret(
    prodId: Long,
    path: String = "path",
    name: String = "name",
    description: String = "description"
) = create(path, name, description, ProductId(prodId))

fun SecretRepository.createRepositorySecret(
    repoId: Long,
    path: String = "path",
    name: String = "name",
    description: String = "description"
) = create(path, name, description, RepositoryId(repoId))
