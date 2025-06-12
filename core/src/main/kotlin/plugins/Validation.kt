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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation

import org.eclipse.apoapsis.ortserver.api.v1.model.CreateInfrastructureService
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.CreateRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.UpdateRepository
import org.eclipse.apoapsis.ortserver.components.secrets.secretsValidations
import org.eclipse.apoapsis.ortserver.shared.ktorutils.mapValidationResult

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

        validate<CreateInfrastructureService> { create ->
            mapValidationResult(CreateInfrastructureService.validate(create))
        }

        validate<CreateRepository> { create ->
            mapValidationResult(CreateRepository.validate(create))
        }

        validate<UpdateRepository> { update ->
            mapValidationResult(UpdateRepository.validate(update))
        }

        secretsValidations()
    }
}
