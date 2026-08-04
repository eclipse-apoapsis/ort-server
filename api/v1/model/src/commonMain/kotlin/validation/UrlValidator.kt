/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.api.v1.model.validation

/**
 * An interface for a URL validator. A concrete implementation can be passed to entities that have a URL property.
 *
 * The background of this interface is that it is rather difficult to come up with a strict validation implementation
 * in a Kotlin multi-platform project. By abstracting the concrete validation logic behind this interface, it is
 * possible to use Java standard classes for this purpose in the _core_ project.
 */
interface UrlValidator {
    companion object {
        /** A placeholder that can be replaced dynamically with a concrete URL type. */
        private const val URL_TYPE_PLACEHOLDER = "<<type>>"

        /** An error message for an invalid URL. */
        private const val INVALID_URL_MESSAGE = "The ${URL_TYPE_PLACEHOLDER}URL is malformed."

        private const val USER_INFO_MESSAGE = "The ${URL_TYPE_PLACEHOLDER}URL must not contain userinfo."

        /**
         * Generate a validation error message for an invalid URL of the given optional [urlType].
         */
        fun invalidUrlMessage(urlType: String? = null): String = replaceUrlType(INVALID_URL_MESSAGE, urlType)

        /**
         * Generate a validation error message for a URL with a user info component of the given optional [urlType].
         */
        fun userInfoMessage(urlType: String? = null): String = replaceUrlType(USER_INFO_MESSAGE, urlType)

        /**
         * Generate a validation error message based on the given [message] with the optional [urlType] replaced in the
         * message.
         */
        private fun replaceUrlType(message: String, urlType: String?): String =
            message.replace(URL_TYPE_PLACEHOLDER, urlType?.let { "$it " }.orEmpty())
    }

    /**
     * Validate if the given [url] is valid.
     */
    fun isValidUrl(url: String): Boolean

    /**
     * Return *true* if the given [url] has a user info component or *false* otherwise.
     */
    fun hasUserInfo(url: String): Boolean
}
