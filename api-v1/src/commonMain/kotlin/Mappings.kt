/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.server.api.v1

import org.ossreviewtoolkit.server.api.v1.AnalyzerJobConfiguration as ApiAnalyzerJobConfiguration
import org.ossreviewtoolkit.server.api.v1.JobConfigurations as ApiJobConfigurations
import org.ossreviewtoolkit.server.api.v1.Organization as ApiOrganization
import org.ossreviewtoolkit.server.api.v1.OrtRun as ApiOrtRun
import org.ossreviewtoolkit.server.api.v1.Product as ApiProduct
import org.ossreviewtoolkit.server.api.v1.Repository as ApiRepository
import org.ossreviewtoolkit.server.api.v1.RepositoryType as ApiRepositoryType
import org.ossreviewtoolkit.server.model.AnalyzerJobConfiguration
import org.ossreviewtoolkit.server.model.JobConfigurations
import org.ossreviewtoolkit.server.model.Organization
import org.ossreviewtoolkit.server.model.OrtRun
import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.util.OptionalValue

fun AnalyzerJobConfiguration.mapToApi() = ApiAnalyzerJobConfiguration(allowDynamicVersions)

fun ApiAnalyzerJobConfiguration.mapToModel() = AnalyzerJobConfiguration(allowDynamicVersions)

fun JobConfigurations.mapToApi() = ApiJobConfigurations(analyzer.mapToApi())

fun ApiJobConfigurations.mapToModel() = JobConfigurations(analyzer.mapToModel())

fun Organization.mapToApi() = ApiOrganization(id, name, description)

fun OrtRun.mapToApi() = ApiOrtRun(id = index, repositoryId, revision, createdAt, jobs.mapToApi())

fun Product.mapToApi() = ApiProduct(id, name, description)

fun Repository.mapToApi() = ApiRepository(id, type.mapToApi(), url)

fun RepositoryType.mapToApi() = ApiRepositoryType.valueOf(name)

fun ApiRepositoryType.mapToModel() = RepositoryType.valueOf(name)

fun OptionalValue<ApiRepositoryType>.mapToModel() = map { it.mapToModel() }
