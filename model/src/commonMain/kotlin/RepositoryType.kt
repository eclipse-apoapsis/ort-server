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

package org.eclipse.apoapsis.ortserver.model

/**
 * A class to represent the type of a source code repository.
 */
data class RepositoryType(val name: String) {
    companion object {
        val GIT = RepositoryType("GIT")
        val GIT_REPO = RepositoryType("GIT_REPO")
        val MERCURIAL = RepositoryType("MERCURIAL")
        val SUBVERSION = RepositoryType("SUBVERSION")
        val UNKNOWN = RepositoryType("")

        private val STANDARD_TYPES = setOf(GIT, GIT_REPO, MERCURIAL, SUBVERSION)

        /**
         * Return a [RepositoryType] instance for the given [type][name]. This can be one of the known standard types
         * or a newly created instance with the given [name].
         */
        fun forName(name: String): RepositoryType =
            if (name.isNotEmpty()) {
                STANDARD_TYPES.find { it.matches(name) } ?: RepositoryType(name)
            } else {
                UNKNOWN
            }
    }

    private fun matches(type: String): Boolean =
        name.replace("_", "").equals(type, ignoreCase = true)
}
