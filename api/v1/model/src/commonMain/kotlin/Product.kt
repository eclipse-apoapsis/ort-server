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

package org.eclipse.apoapsis.ortserver.api.v1.model

import io.konform.validation.Validation
import io.konform.validation.constraints.pattern

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.api.v1.model.validation.ValidatorFunc
import org.eclipse.apoapsis.ortserver.api.v1.model.validation.optionalPattern

/**
 * Response object for the products endpoints. Used to group multiple repositories into products.
 */
@Serializable
data class Product(
    val id: Long,

    /** The id of the [Organization] this product belongs to. */
    val organizationId: Long,

    /** The name of the product which has to be unique within an organization. */
    val name: String,

    /** The optional description of a product. */
    val description: String? = null
) {
    companion object {
        val NAME_PATTERN_REGEX = """^(?!\s)[^<>%\\]*(?<!\s)$""".toRegex()
        const val NAME_PATTERN_MESSAGE = "The entity name must not contain chevrons, percents or backslashes. Also " +
                "leading or trailing whitespaces are not allowed."
    }
}

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
                    pattern(Product.NAME_PATTERN_REGEX) hint Product.NAME_PATTERN_MESSAGE
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
                    optionalPattern(Product.NAME_PATTERN_REGEX) hint Product.NAME_PATTERN_MESSAGE
                }
            }.invoke(obj)
        }
    }
}
