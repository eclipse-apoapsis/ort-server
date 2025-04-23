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

import java.io.InputStream

/**
 * A service provider interface for reading configuration files from a configuration storage.
 *
 * This interface is used within ORT Server when no single configuration properties are needed, but whole files.
 * Examples include files with Evaluator rules, templates for the Reporter, license classification files, etc. The
 * model used by this class is a logic file system that is identified by a [Context] object. In this file system, the
 * desired configuration files can be selected via instances of the [Path] class.
 */
interface ConfigFileProvider {
    /**
     * Return a resolved or normalized [Context] for the passed in [context]. This function is useful for contexts
     * that can change over time, for instance, if configuration files are stored in a VCS in a specific branch.
     * Throughout an ORT run, the same configuration files should be used. Therefore, at the beginning, the context is
     * resolved and stored, so that it can be reused later.
     */
    fun resolveContext(context: Context): Context

    /**
     * Return an [InputStream] for reading the content of the configuration file with the given [path] in the given
     * [context]. Throw an exception if the file cannot be resolved or access is not possible for whatever reason.
     */
    fun getFile(context: Context, path: Path): InputStream

    /**
     * Return a flag whether there is a configuration file with the given [path] in the given [context]. If this
     * function returns *true*, it should be possible to actually read this file via the [getFile] function.
     */
    fun contains(context: Context, path: Path): Boolean

    /**
     * Return a [Set] with the [Path]s to the configuration files contained in the given [path] and [context]. Throw
     * an exception if the [path] is invalid, e.g., does not point to an existing subdirectory.
     */
    fun listFiles(context: Context, path: Path): Set<Path>
}
