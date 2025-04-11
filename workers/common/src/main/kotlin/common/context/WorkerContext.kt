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

package org.eclipse.apoapsis.ortserver.workers.common.context

import java.io.File

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.config.Path
import org.eclipse.apoapsis.ortserver.model.Hierarchy
import org.eclipse.apoapsis.ortserver.model.InfrastructureService
import org.eclipse.apoapsis.ortserver.model.OrtRun
import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.model.ProviderPluginConfiguration
import org.eclipse.apoapsis.ortserver.model.Secret

/**
 * An interface providing information and services useful to multiple worker implementations.
 *
 * Workers requiring this functionality can obtain an instance from a [WorkerContextFactory].
 *
 * Some functionality of this interface can consume resources that should be released when they are no longer needed.
 * This can be done via the [close] function.
 */
interface WorkerContext : AutoCloseable {
    /** The [OrtRun] that is to be processed. */
    val ortRun: OrtRun

    /** An object with information about the current repository and its hierarchy. */
    val hierarchy: Hierarchy

    /** The object providing access to the ORT Server configuration. */
    val configManager: ConfigManager

    /**
     * Return a new temporary directory that can be used by a worker to deal with temporary files. When this context
     * is closed this directory is deleted with all its content.
     */
    fun createTempDir(): File

    /**
     * Resolve the given [secret] and return its value. Cache the value, so that it can be returned directly when a
     * [Secret] with the same path is queried again.
     */
    suspend fun resolveSecret(secret: Secret): String

    /**
     * Resolve the given [secrets] in parallel and return a map with their values. Also add them to the internal cache,
     * so that they are directly available when their values are queried via [resolveSecret]. For clients having to
     * deal with multiple secrets, using this function is more efficient than multiple calls of [resolveSecret] in a
     * sequence.
     */
    suspend fun resolveSecrets(vararg secrets: Secret): Map<Secret, String>

    /**
     * Resolve all the secrets referenced by the given [config]. If [config] is not *null*, obtain the referenced
     * secrets from the [PluginConfig]s and resolve all values using the [configManager] instance. Note that
     * in contrast to [resolveSecrets], this function deals with secrets from the configuration instead of secrets
     * managed on behalf of customers.
     */
    suspend fun resolvePluginConfigSecrets(config: Map<String, PluginConfig>?): Map<String, PluginConfig>

    /**
     * Resolve all the secrets referenced by the given [config]. If [config] is not *null*, obtain the referenced
     * secrets from the [ProviderPluginConfiguration]s and resolve all values using the [configManager] instance. Note
     * that in contrast to [resolveSecrets], this function deals with secrets from the configuration instead of secrets
     * managed on behalf of customers.
     */
    suspend fun resolveProviderPluginConfigSecrets(
        config: List<ProviderPluginConfiguration>?
    ): List<ProviderPluginConfiguration>

    /**
     * Download the configuration file at the specified [path] from the resolved configuration context to the given
     * [directory]. Optionally, override the file name with the given [targetName].
     */
    suspend fun downloadConfigurationFile(path: Path, directory: File, targetName: String? = null): File

    /**
     * Download all the configuration files in the given [paths] collection from the resolved configuration context to
     * the given [directory]. Return a [Map] that allows access to the resulting files by their paths.
     */
    suspend fun downloadConfigurationFiles(paths: Collection<Path>, directory: File): Map<Path, File>

    /**
     * Download all the files contained in the configuration directory at the specified [path] to the given
     * [targetDirectory]. Return a [Map] that allows access to the resulting files by their paths. Note that this
     * function does not handle nested folder structures; only the direct children of the specified directory are
     * downloaded.
     */
    suspend fun downloadConfigurationDirectory(path: Path, targetDirectory: File): Map<Path, File>

    /**
     * Install the given list of [services] in ORT Server's authenticator, so that their credentials can be used to
     * access the corresponding URLs. Optionally, install the given [listener] for authentication events.
     */
    suspend fun setupAuthentication(
        services: Collection<InfrastructureService>,
        listener: AuthenticationListener? = null
    )
}
