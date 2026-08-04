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

import java.net.URI
import java.net.URL

import org.eclipse.apoapsis.ortserver.api.v1.model.PatchOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.PatchProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.PatchRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.PostOrganization
import org.eclipse.apoapsis.ortserver.api.v1.model.PostProduct
import org.eclipse.apoapsis.ortserver.api.v1.model.PostRepository
import org.eclipse.apoapsis.ortserver.api.v1.model.validation.UrlValidator
import org.eclipse.apoapsis.ortserver.components.infrastructureservices.infrastructureServicesValidations
import org.eclipse.apoapsis.ortserver.components.secrets.secretsValidations
import org.eclipse.apoapsis.ortserver.shared.ktorutils.mapValidationResult

fun Application.configureValidation() {
    install(RequestValidation) {
        validate<PostOrganization> { create ->
            mapValidationResult(PostOrganization.validate(create))
        }

        validate<PatchOrganization> { update ->
            mapValidationResult(PatchOrganization.validate(update))
        }

        validate<PostProduct> { create ->
            mapValidationResult(PostProduct.validate(create))
        }

        validate<PatchProduct> { update ->
            mapValidationResult(PatchProduct.validate(update))
        }

        validate<PostRepository> { create ->
            mapValidationResult(PostRepository.validate(StrictUrlValidator)(create))
        }

        validate<PatchRepository> { update ->
            mapValidationResult(PatchRepository.validate(StrictUrlValidator)(update))
        }

        infrastructureServicesValidations()
        secretsValidations()
    }
}

/**
 * An implementation of the [UrlValidator] interface that uses Java standard classes to perform a strict validation of
 * URLs.
 */
private object StrictUrlValidator : UrlValidator {
    override fun isValidUrl(url: String): Boolean = parseUrl(url) != null

    override fun hasUserInfo(url: String): Boolean = parseUrl(url)?.userInfo != null

    /**
     * Parse the given [url] to a [URL] if it is valid. Return *null* otherwise.
     */
    private fun parseUrl(url: String): URL? = runCatching { URI.create(url).toURL() }.getOrNull()
}
