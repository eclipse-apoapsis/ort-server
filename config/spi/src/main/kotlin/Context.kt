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

package org.eclipse.apoapsis.ortserver.config

/**
 * A class representing the requested config context. This context still needs to be resolved to a
 * [ResolvedConfigContext] before it can be used to access the application config.
 *
 * The config context is used to access different versions of the configuration files provided by the
 * [ConfigFileProvider]. The exact meaning depends on the implementation of the [ConfigFileProvider]. For example,
 * if the provider reads the config files from a Git repository, the [RequestedConfigContext] could be a branch name,
 * and the [ResolvedConfigContext] could be the corresponding commit hash.
 */
@JvmInline
value class RequestedConfigContext(val name: String) {
    companion object {
        /**
         * An empty [RequestedConfigContext] that indicates that the user has not requested a specific context. The
         * interpretation depends on the [ConfigFileProvider] implementation. For example, it could mean the default
         * branch of a Git repository.
         */
        val EMPTY = RequestedConfigContext("")
    }
}

/**
 * A class representing the resolved config context. A resolved config context is required to read files from a
 * [ConfigFileProvider]. Also see [RequestedConfigContext].
 */
@JvmInline
value class ResolvedConfigContext(val name: String) {
    companion object {
        /**
         * An empty [ResolvedConfigContext] that can be used when no specific context is required, for example, by
         * providers that do not support different contexts.
         */
        val EMPTY = ResolvedConfigContext("")
    }
}
