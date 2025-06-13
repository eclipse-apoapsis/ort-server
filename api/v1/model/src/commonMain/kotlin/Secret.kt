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

package org.eclipse.apoapsis.ortserver.api.v1.model

import io.konform.validation.Validation
import io.konform.validation.constraints.pattern

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.api.v1.model.validation.ValidatorFunc
import org.eclipse.apoapsis.ortserver.shared.apimodel.OptionalValue

/**
 * Response object for the Secrets Metadata endpoint.
 */
@Serializable
data class Secret(
    /**
     * The name of the secret.
     */
    val name: String,

    /**
     * The description of the secret.
     */
    val description: String?
)

/**
 * Request object for the create secret endpoint.
 */
@Serializable
data class CreateSecret(
    val name: String,
    val value: String,
    val description: String?
) {
    companion object {
        val NAME_PATTERN_REGEX = """^(?!\s)[A-Za-z0-9- ]*(?<!\s)$""".toRegex()
        const val NAME_PATTERN_MESSAGE = "The entity name may only contain letters, numbers, hyphen marks and " +
                "spaces. Leading and trailing whitespaces are not allowed."

        val validate: ValidatorFunc<CreateSecret> = { obj ->
            Validation {
                CreateSecret::name {
                    pattern(NAME_PATTERN_REGEX) hint NAME_PATTERN_MESSAGE
                }
            }.invoke(obj)
        }
    }
}

/**
 * Request object for the update secret endpoint.
 * This holds only the secret value and the description. The secret name is a path variable.
 */
@Serializable
data class UpdateSecret(
    val value: OptionalValue<String> = OptionalValue.Absent,
    val description: OptionalValue<String> = OptionalValue.Absent,
)
