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

package org.eclipse.apoapsis.ortserver.model

import io.konform.validation.Invalid
import io.konform.validation.Validation
import io.konform.validation.constraints.pattern

import java.util.EnumSet

import org.eclipse.apoapsis.ortserver.model.validation.ValidationException

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
    val product: Product?,

    /**
     * The set of [CredentialsType]s for this infrastructure service. This determines in which configuration files the
     * credentials of the service are listed when generating the runtime environment for a worker. Per default, the
     * credentials for all the infrastructure services referenced from a repository are added to the _.netrc_ file.
     * This is typically desired, so that all external tools invoked from a worker can access the credentials they
     * represent. In some constellations, however, there could be conflicting services; for instance, if multiple
     * repositories with different credentials are defined on the same repository server. Such issues can be resolved
     * by explicitly removing the [CredentialsType.NETRC_FILE] constant from this set. It is also possible to add other
     * constants if a special treatment of the credentials is required.
     */
    val credentialsTypes: Set<CredentialsType> = EnumSet.of(CredentialsType.NETRC_FILE)
) {
    companion object {
        val NAME_PATTERN_REGEX = """^(?!\s)[A-Za-z0-9- ]*(?<!\s)$""".toRegex()
        const val NAME_PATTERN_MESSAGE = "The entity name may only contain letters, numbers, hyphen marks and " +
                "spaces. Leading and trailing whitespaces are not allowed."
    }

    fun validate() {
        val validationResult = Validation {
            InfrastructureService::name {
                pattern(NAME_PATTERN_REGEX) hint NAME_PATTERN_MESSAGE
            }
        }.validate(this)

        if (validationResult is Invalid) {
             throw ValidationException(
                validationResult.errors.joinToString("; ") { error -> "'$name': ${error.message}" }
            )
        }
    }
}
