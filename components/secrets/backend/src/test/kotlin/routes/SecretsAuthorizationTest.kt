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

package org.eclipse.apoapsis.ortserver.components.secrets.routes

import io.ktor.client.request.get
import org.eclipse.apoapsis.ortserver.components.authorization.permissions.OrganizationPermission
import org.eclipse.apoapsis.ortserver.components.secrets.secretsRoutes
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest

//class SecretsAuthorizationTest : AbstractAuthorizationTest({
//    "GetSecretsByOrganizationId" should {
//        "require OrganizationPermission.READ" {
//            val createdOrg = dbExtension.fixtures.createOrganization()
//            requestShouldRequireRole(
//                routes = { secretsRoutes(secretService) },
//                role = OrganizationPermission.READ.roleName(createdOrg.id)
//            ) {
//                get("/api/v1/organizations/${createdOrg.id}/secrets")
//            }
//        }
//    }
//})
