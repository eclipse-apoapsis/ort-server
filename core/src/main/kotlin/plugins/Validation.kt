/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.core.plugins

import io.konform.validation.Invalid
import io.konform.validation.ValidationResult as KonformValidationResult

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult as KtorValidationResult

import org.ossreviewtoolkit.server.api.v1.CreateOrganization
import org.ossreviewtoolkit.server.api.v1.CreateProduct
import org.ossreviewtoolkit.server.api.v1.UpdateOrganization
import org.ossreviewtoolkit.server.api.v1.UpdateProduct

fun Application.configureValidation() {
    install(RequestValidation) {
        validate<CreateOrganization> { create ->
            mapValidationResult(CreateOrganization.validate(create))
        }

        validate<UpdateOrganization> { update ->
            mapValidationResult(UpdateOrganization.validate(update))
        }

        validate<CreateProduct> { create ->
            mapValidationResult(CreateProduct.validate(create))
        }

        validate<UpdateProduct> { update ->
            mapValidationResult(UpdateProduct.validate(update))
        }
    }
}

private fun mapValidationResult(result: KonformValidationResult<*>): KtorValidationResult {
    return when (result) {
        is Invalid<*> -> KtorValidationResult.Invalid(result.errors.map { error -> error.message })
        else -> KtorValidationResult.Valid
    }
}
