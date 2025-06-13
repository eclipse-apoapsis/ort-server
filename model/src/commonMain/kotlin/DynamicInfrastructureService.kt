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

import org.eclipse.apoapsis.ortserver.model.validation.ValidationException

/**
 * A data class describing a dynamic infrastructure service that is referenced during an ORT run.
 *
 * In contract to [InfrastructureService], this class contains data that is collected at the start of the ORT run
 * from the environment definition file .ort.env.yml. As this file is located inside the repository being analyzed,
 * and may change in every revision, the data is highly dynamic compared to the more static [InfrastructureService]
 * data. There is no direct reference to an [Organization], [Product] or [Repository] in this class, as these
 * references can be found in the assoicated ORT run.
 *
 * As the data in this class is ephemeral, it holds no strong references to [Secret]s, but rather references the
 * secrets by their names.
 */
data class DynamicInfrastructureService(
    /** The name of this service. */
    val name: String,

    /** The URL of this service. */
    val url: String,

    /** The name of the [Secret] that contains the username of the credentials for this infrastructure service. */
    val usernameSecretName: String,

    /** The name of the [Secret] that contains the password of the credentials for this infrastructure service. */
    val passwordSecretName: String,

    /**
     * The set of [CredentialsType]s for this infrastructure service. This determines in which configuration files the
     * credentials of the service are listed when generating the runtime environment for a worker. All services
     * involved in an ORT run are installed in the authenticator, so that their credentials are available when
     * accessing the corresponding URLs from within the JVM. If the credentials are also required from external tools
     * (e.g., the Git CLI), this needs to be indicated by adding the corresponding constant. Per default, the set
     * is empty, so that the services are only used by the authenticator of the JVM.
     */
    val credentialsTypes: Set<CredentialsType> = emptySet()
) {
    companion object {
        val NAME_PATTERN_REGEX = """^(?!\s)[A-Za-z0-9- ]*(?<!\s)$""".toRegex()
        const val NAME_PATTERN_MESSAGE = "The entity name may only contain letters, numbers, hyphen marks and " +
                "spaces. Leading and trailing whitespaces are not allowed."
    }

    fun validate() {
        val validationResult = Validation {
            DynamicInfrastructureService::name {
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
