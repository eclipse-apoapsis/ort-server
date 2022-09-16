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

package org.ossreviewtoolkit.server.core.api

import org.ossreviewtoolkit.server.model.Organization
import org.ossreviewtoolkit.server.model.Product
import org.ossreviewtoolkit.server.model.Repository
import org.ossreviewtoolkit.server.model.RepositoryType
import org.ossreviewtoolkit.server.model.util.OptionalValue
import org.ossreviewtoolkit.server.shared.models.api.Organization as ApiOrganization
import org.ossreviewtoolkit.server.shared.models.api.Product as ApiProduct
import org.ossreviewtoolkit.server.shared.models.api.Repository as ApiRepository
import org.ossreviewtoolkit.server.shared.models.api.RepositoryType as ApiRepositoryType
import org.ossreviewtoolkit.server.shared.models.api.common.OptionalValue as ApiOptionalValue

fun Organization.mapToApi() = ApiOrganization(id, name, description)

fun Product.mapToApi() = ApiProduct(id, name, description)

fun Repository.mapToApi() = ApiRepository(id, type.mapToApi(), url)

fun RepositoryType.mapToApi() = ApiRepositoryType.valueOf(name)

inline fun <reified T> ApiOptionalValue<T>.mapToModel() =
    when (this) {
        is ApiOptionalValue.Present -> OptionalValue.Present(value)
        is ApiOptionalValue.Absent -> OptionalValue.Absent
    }

fun ApiRepositoryType.mapToModel() = RepositoryType.valueOf(name)
