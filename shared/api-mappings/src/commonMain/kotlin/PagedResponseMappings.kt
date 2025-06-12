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

package org.eclipse.apoapsis.ortserver.shared.apimappings

import org.eclipse.apoapsis.ortserver.model.util.ListQueryParameters
import org.eclipse.apoapsis.ortserver.model.util.ListQueryResult
import org.eclipse.apoapsis.ortserver.model.util.OrderDirection
import org.eclipse.apoapsis.ortserver.model.util.OrderField
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagedResponse
import org.eclipse.apoapsis.ortserver.shared.apimodel.PagingOptions
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortDirection
import org.eclipse.apoapsis.ortserver.shared.apimodel.SortProperty

fun <T, E> ListQueryResult<T>.mapToApi(mapValues: (T) -> E) =
    PagedResponse(
        data = data.map(mapValues),
        pagination = params.mapToApi().toPagingData(totalCount)
    )

fun ListQueryParameters.mapToApi() =
    PagingOptions(
        limit = limit,
        offset = offset,
        sortProperties = sortFields.map { it.mapToApi() }
    )

fun OrderField.mapToApi() = SortProperty(name, direction.mapToApi())

fun OrderDirection.mapToApi() =
    when (this) {
        OrderDirection.ASCENDING -> SortDirection.ASCENDING
        OrderDirection.DESCENDING -> SortDirection.DESCENDING
    }

fun PagingOptions.mapToModel() =
    ListQueryParameters(
        sortFields = sortProperties?.map { it.mapToModel() }.orEmpty(),
        limit = limit,
        offset = offset
    )

fun SortProperty.mapToModel() = OrderField(name, direction.mapToModel())

fun SortDirection.mapToModel() =
    when (this) {
        SortDirection.ASCENDING -> OrderDirection.ASCENDING
        SortDirection.DESCENDING -> OrderDirection.DESCENDING
    }
