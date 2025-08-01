/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import org.eclipse.apoapsis.ortserver.model.util.OptionalValue
import org.eclipse.apoapsis.ortserver.shared.apimodel.OptionalValue as ApiOptionalValue

fun <T> ApiOptionalValue<T>.mapToModel() = mapToModel { it }

fun <IN, OUT> ApiOptionalValue<IN>.mapToModel(valueMapping: (IN) -> OUT): OptionalValue<OUT> =
    when (this) {
        is ApiOptionalValue.Present -> OptionalValue.Present(valueMapping(value))
        is ApiOptionalValue.Absent -> OptionalValue.Absent
    }
