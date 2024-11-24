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

package org.eclipse.apoapsis.ortserver.api.v1.model.validation

import io.konform.validation.ValidationBuilder
import io.konform.validation.ValidationResult

import org.eclipse.apoapsis.ortserver.api.v1.model.OptionalValue

/**
 * A typealias containing an argument and a return type for a validation function that should be implemented in entity
 * classes to reduce boilerplate code.
 */
typealias ValidatorFunc<T> = (T) -> ValidationResult<T>

/**
 * An extension function for validating [OptionalValue] objects against a given regex [pattern].
 */
fun ValidationBuilder<OptionalValue<String>>.optionalPattern(pattern: Regex) = constrain(
    "must match the expected pattern '${pattern.pattern}'"
) {
    when (it) {
        is OptionalValue.Present -> it.value.matches(pattern)
        is OptionalValue.Absent -> true
    }
}
