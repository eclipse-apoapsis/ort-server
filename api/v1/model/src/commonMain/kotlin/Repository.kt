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

import org.eclipse.apoapsis.ortserver.api.v1.model.validation.UrlValidator
import org.eclipse.apoapsis.ortserver.api.v1.model.validation.ValidatorFunc
import org.eclipse.apoapsis.ortserver.shared.apimodel.OptionalValue
import org.eclipse.apoapsis.ortserver.shared.apimodel.valueOrThrow

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

    /** The name of the repository. */
    val name: String? = null,

    /** The description of the repository. */
    val description: String? = null
) {
    companion object {
        const val REPOSITORY_URL_TYPE = "repository"
        val NAME_PATTERN_REGEX = Product.NAME_PATTERN_REGEX
        const val NAME_PATTERN_MESSAGE = Product.NAME_PATTERN_MESSAGE
    }
}

/**
 * Request object for the create repository endpoint.
 */
@Serializable
data class PostRepository(
    val type: RepositoryType,
    val url: String,
    val name: String? = null,
    val description: String? = null
) {
    companion object {
        fun validate(urlValidator: UrlValidator): ValidatorFunc<PostRepository> = { obj ->
            Validation {
                PostRepository::url {
                    constrain("malformed URL") {
                        urlValidator.isValidUrl(it)
                    } hint UrlValidator.invalidUrlMessage(Repository.REPOSITORY_URL_TYPE)

                    constrain("URL cannot contain userinfo") {
                        !urlValidator.hasUserInfo(it)
                    } hint UrlValidator.userInfoMessage(Repository.REPOSITORY_URL_TYPE)
                }

                PostRepository::name ifPresent {
                    pattern(Repository.NAME_PATTERN_REGEX) hint Repository.NAME_PATTERN_MESSAGE
                }
            }.invoke(obj)
        }
    }
}

/**
 * Request object for the update repository endpoint.
 */
@Serializable
data class PatchRepository(
    val type: OptionalValue<RepositoryType> = OptionalValue.Absent,
    val url: OptionalValue<String> = OptionalValue.Absent,
    val name: OptionalValue<String?> = OptionalValue.Absent,
    val description: OptionalValue<String?> = OptionalValue.Absent,

    /**
     * The id of the product this repository belongs to. This is only used when moving the repository to another
     * product.
     */
    val productId: OptionalValue<Long> = OptionalValue.Absent
) {
    companion object {
        fun validate(urlValidator: UrlValidator): ValidatorFunc<PatchRepository> = { obj ->
            Validation {
                PatchRepository::url {
                    constrain("malformed URL") {
                        when (it) {
                            is OptionalValue.Present -> urlValidator.isValidUrl(it.value)
                            is OptionalValue.Absent -> true
                        }
                    } hint UrlValidator.invalidUrlMessage(Repository.REPOSITORY_URL_TYPE)

                    constrain("URL cannot contain userinfo") {
                        when (it) {
                            is OptionalValue.Present -> !urlValidator.hasUserInfo(it.valueOrThrow)
                            is OptionalValue.Absent -> true
                        }
                    } hint UrlValidator.userInfoMessage(Repository.REPOSITORY_URL_TYPE)
                }

                PatchRepository::name {
                    constrain("must match the expected pattern '${Repository.NAME_PATTERN_REGEX.pattern}'") {
                        when (it) {
                            is OptionalValue.Present -> it.value?.matches(Repository.NAME_PATTERN_REGEX) ?: true
                            is OptionalValue.Absent -> true
                        }
                    } hint Repository.NAME_PATTERN_MESSAGE
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
