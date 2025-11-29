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

package ort.eclipse.apoapsis.ortserver.components.search

import org.eclipse.apoapsis.ortserver.components.search.apimodel.RunWithPackage
import org.eclipse.apoapsis.ortserver.dao.test.Fixtures
import org.eclipse.apoapsis.ortserver.model.runs.Identifier

fun createRunWithPackage(
    fixtures: Fixtures,
    repoId: Long = -1L,
    pkgId: Identifier = Identifier("test", "ns", "name", "ver")
): RunWithPackage {
    val pkg = fixtures.generatePackage(pkgId)
    val ortRun = fixtures.createAnalyzerRunWithPackages(packages = setOf(pkg), repositoryId = repoId)

    return RunWithPackage(
        organizationId = ortRun.organizationId,
        productId = ortRun.productId,
        repositoryId = ortRun.repositoryId,
        ortRunId = ortRun.id,
        revision = ortRun.revision,
        createdAt = ortRun.createdAt,
        packageId = pkgId.toCoordinates()
    )
}

fun Identifier.toCoordinates(): String = "$type:$namespace:$name:$version"
