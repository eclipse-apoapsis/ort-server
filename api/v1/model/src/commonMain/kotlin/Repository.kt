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

import io.ktor.http.parseUrl

import kotlinx.serialization.Serializable

import org.eclipse.apoapsis.ortserver.api.v1.model.validation.ValidatorFunc

/**
 * Response object for the repository endpoint.
 */
@Serializable
data class Repository(
    val id: Long,

    /** The id of the [Organization] this repository belongs to. */
    val organizationId: Long,

    /** The id of the [Product] this repository belongs to. */
    val productId: Long,

    /** The type of the repository. */
    val type: RepositoryType,

    /** The url to the repository. */
    val url: String,

    /** The description of the repository. */
    val description: String? = null
) {
    companion object {
        const val INVALID_URL_MESSAGE = "The repository URL is malformed."
        const val USER_INFO_MESSAGE = "The repository URL must not contain userinfo."

        fun isValidUrl(url: String): Boolean = parseUrl(url)?.host?.isValidHost() ?: false

        fun hasUserInfo(url: String): Boolean = parseUrl(url)?.user != null
    }
}

/**
 * Check if there are only valid characters in the host.
 */
private fun String.isValidHost() = all { it.isLetterOrDigit() || it == '.' || it == '-' }

/**
 * Request object for the create repository endpoint.
 */
@Serializable
data class CreateRepository(
    val type: RepositoryType,
    val url: String,
    val description: String? = null
) {
    companion object {
        val validate: ValidatorFunc<CreateRepository> = { obj ->
            Validation {
                CreateRepository::url {
                    constrain("malformed URL") {
                        Repository.isValidUrl(it)
                    } hint Repository.INVALID_URL_MESSAGE

                    constrain("URL cannot contain userinfo") {
                        !Repository.hasUserInfo(it)
                    } hint Repository.USER_INFO_MESSAGE
                }
            }.invoke(obj)
        }
    }
}

/**
 * Request object for the update repository endpoint.
 */
@Serializable
data class UpdateRepository(
    val type: OptionalValue<RepositoryType> = OptionalValue.Absent,
    val url: OptionalValue<String> = OptionalValue.Absent,
    val description: OptionalValue<String?> = OptionalValue.Absent
) {
    companion object {
        val validate: ValidatorFunc<UpdateRepository> = { obj ->
            Validation {
                UpdateRepository::url {
                    constrain("malformed URL") {
                        when (it) {
                            is OptionalValue.Present -> Repository.isValidUrl(it.value)
                            is OptionalValue.Absent -> true
                        }
                    } hint Repository.INVALID_URL_MESSAGE

                    constrain("URL cannot contain userinfo") {
                        when (it) {
                            is OptionalValue.Present -> !Repository.hasUserInfo(it.valueOrThrow)
                            is OptionalValue.Absent -> true
                        }
                    } hint Repository.USER_INFO_MESSAGE
                }
            }.invoke(obj)
        }
    }
}

enum class RepositoryType {
    GIT,
    GIT_REPO,
    MERCURIAL,
    SUBVERSION
}
