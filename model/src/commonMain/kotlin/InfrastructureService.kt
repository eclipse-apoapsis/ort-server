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

package org.ossreviewtoolkit.server.model

import io.konform.validation.Invalid
import io.konform.validation.Validation
import io.konform.validation.jsonschema.pattern

import org.ossreviewtoolkit.server.model.validation.Constraints.namePatternMessage
import org.ossreviewtoolkit.server.model.validation.Constraints.namePatternRegex
import org.ossreviewtoolkit.server.model.validation.ValidationException

/**
 * A data class describing an infrastructure service that is referenced during an ORT run.
 *
 * A repository being analyzed by ORT can declare that it requires specific infrastructure services, such as source
 * code or artifact repositories to resolve its dependencies. ORT Server takes those declarations into account when
 * setting up the build environment for a repository. They are also needed to determine the credentials required to
 * access the corresponding service.
 */
data class InfrastructureService(
    /** The name of this service. */
    val name: String,

    /** The URL of this service. */
    val url: String,

    /** An optional description for this infrastructure service. */
    val description: String? = null,

    /** The [Secret] that contains the username of the credentials for this infrastructure service. */
    val usernameSecret: Secret,

    /** The [Secret] that contains the password of the credentials for this infrastructure service. */
    val passwordSecret: Secret,

    /** The [Organization] this infrastructure belongs to if any. */
    val organization: Organization?,

    /** The [Product] this infrastructure service belongs to if any. */
    val product: Product?
) {

    fun validate() {
        val validationResult = Validation {
            InfrastructureService::name {
                pattern(namePatternRegex) hint namePatternMessage
            }
        }.validate(this)

        if (validationResult is Invalid) {
             throw ValidationException(
                validationResult.errors.joinToString("; ") { error -> error.message }
            )
        }
    }
}
