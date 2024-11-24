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

import java.util.EnumSet

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.api.v1.model.validation.ValidatorFunc

/**
 * The response object for the endpoint to manage infrastructure services.
 */
@Serializable
data class InfrastructureService(
    /** The name of this service. */
    val name: String,

    /** The URL of this service. */
    val url: String,

    /** An optional description for this infrastructure service. */
    val description: String? = null,

    /**
     * The reference to the secret that contains the username of the credentials for this infrastructure service.
     * The reference contains the name of the secret and optionally the structure it belongs to.
     */
    val usernameSecretRef: String,

    /** The reference to the secret that contains the password of the credentials for this infrastructure service. */
    val passwordSecretRef: String,

    /**
     * The set of [CredentialsType]s for this infrastructure service. This determines in which configuration files the
     * credentials of the service are listed when generating the runtime environment for a worker.
     */
    val credentialsTypes: Set<CredentialsType> = EnumSet.of(CredentialsType.NETRC_FILE)
)

/**
 * Request object for the create infrastructure service endpoint.
 */
@Serializable
data class CreateInfrastructureService(
    val name: String,
    val url: String,
    val description: String? = null,
    val usernameSecretRef: String,
    val passwordSecretRef: String,
    val credentialsTypes: Set<CredentialsType> = EnumSet.of(CredentialsType.NETRC_FILE)
) {
    companion object {
        val NAME_PATTERN_REGEX = """^(?!\s)[A-Za-z0-9- ]*(?<!\s)$""".toRegex()
        const val NAME_PATTERN_MESSAGE = "The entity name may only contain letters, numbers, hyphen marks and " +
                "spaces. Leading and trailing whitespaces are not allowed."

        val validate: ValidatorFunc<CreateInfrastructureService> = { obj ->
            Validation {
                CreateInfrastructureService::name {
                    pattern(NAME_PATTERN_REGEX) hint NAME_PATTERN_MESSAGE
                }
            }.invoke(obj)
        }
    }
}

/**
 * Request object for the update infrastructure service endpoint.
 */
@Serializable
data class UpdateInfrastructureService(
    val url: OptionalValue<String> = OptionalValue.Absent,
    val description: OptionalValue<String?> = OptionalValue.Absent,
    val usernameSecretRef: OptionalValue<String> = OptionalValue.Absent,
    val passwordSecretRef: OptionalValue<String> = OptionalValue.Absent,
    val credentialsTypes: OptionalValue<Set<CredentialsType>> = OptionalValue.Absent
)
