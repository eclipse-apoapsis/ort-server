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

package org.ossreviewtoolkit.server.api.v1

import io.konform.validation.Validation
import io.konform.validation.jsonschema.pattern

import kotlinx.serialization.Serializable

import org.ossreviewtoolkit.server.api.v1.validation.Constraints.namePatternMessage
import org.ossreviewtoolkit.server.api.v1.validation.Constraints.namePatternRegex
import org.ossreviewtoolkit.server.api.v1.validation.ValidatorFunc
import org.ossreviewtoolkit.server.api.v1.validation.optionalPattern
import org.ossreviewtoolkit.server.model.util.OptionalValue

/**
 * Response object for the products endpoints. Used to group multiple repositories into products.
 */
@Serializable
data class Product(
    val id: Long,

    /** The name of the product which has to be unique within an organization. */
    val name: String,

    /** The optional description of a product. */
    val description: String? = null
)

/**
 * Request object for the create product endpoint.
 */
@Serializable
data class CreateProduct(
    val name: String,
    val description: String? = null
) {
    companion object {
        val validate: ValidatorFunc<CreateProduct> = { obj ->
            Validation {
                CreateProduct::name {
                    pattern(namePatternRegex) hint namePatternMessage
                }
            }.invoke(obj)
        }
    }
}

/**
 * Request object for the update product endpoint.
 */
@Serializable
data class UpdateProduct(
    val name: OptionalValue<String> = OptionalValue.Absent,
    val description: OptionalValue<String?> = OptionalValue.Absent
) {
    companion object {
        val validate: ValidatorFunc<UpdateProduct> = { obj ->
            Validation {
                UpdateProduct::name {
                    optionalPattern(namePatternRegex) hint namePatternMessage
                }
            }.invoke(obj)
        }
    }
}
