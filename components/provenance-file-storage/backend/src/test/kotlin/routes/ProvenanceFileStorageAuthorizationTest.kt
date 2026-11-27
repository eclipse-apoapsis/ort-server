/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.provenancefilestorage.routes

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode

import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileStorageService
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.ProvenanceFileType
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.createFakeStorage
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.provenanceFileStorageRoutes
import org.eclipse.apoapsis.ortserver.components.provenancefilestorage.toProvenanceFileId
import org.eclipse.apoapsis.ortserver.shared.ktorutils.AbstractAuthorizationTest
import org.eclipse.apoapsis.ortserver.storage.Key

class ProvenanceFileStorageAuthorizationTest : AbstractAuthorizationTest({
    lateinit var service: ProvenanceFileStorageService

    beforeEach {
        service = ProvenanceFileStorageService(createFakeStorage().first, createFakeStorage().first)
    }

    ProvenanceFileType.entries.forEach { type ->
        "GetProvenanceFiles for '${type.pathSegment}'" should {
            "require the superuser role" {
                requestShouldRequireSuperuser(routes = { provenanceFileStorageRoutes(service) }) {
                    get("/admin/${type.pathSegment}")
                }
            }
        }

        "GetProvenanceFileStatistics for '${type.pathSegment}'" should {
            "require the superuser role" {
                requestShouldRequireSuperuser(routes = { provenanceFileStorageRoutes(service) }) {
                    get("/admin/${type.pathSegment}/statistics")
                }
            }
        }

        "DownloadProvenanceFile for '${type.pathSegment}'" should {
            "require the superuser role" {
                requestShouldRequireSuperuser(
                    routes = { provenanceFileStorageRoutes(service) },
                    successStatus = HttpStatusCode.NotFound
                ) {
                    get("/admin/${type.pathSegment}/${Key("key").toProvenanceFileId()}/download")
                }
            }
        }

        "DeleteProvenanceFile for '${type.pathSegment}'" should {
            "require the superuser role" {
                requestShouldRequireSuperuser(
                    routes = { provenanceFileStorageRoutes(service) },
                    successStatus = HttpStatusCode.NotFound
                ) {
                    delete("/admin/${type.pathSegment}/${Key("key").toProvenanceFileId()}")
                }
            }
        }
    }
})
