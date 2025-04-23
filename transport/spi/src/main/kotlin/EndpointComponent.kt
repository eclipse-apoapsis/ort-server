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

package org.eclipse.apoapsis.ortserver.transport

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import java.io.File

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

import org.eclipse.apoapsis.ortserver.config.ConfigManager

import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * An abstract base class providing functionality useful for components implementing endpoints in the ORT server.
 *
 * Implementations for concrete endpoints can extend this class. The main entrypoint function for this endpoint then
 * just has to create an instance and invoke the [start] function. This makes the following functionality available:
 *
 * - The endpoint-specific configuration is loaded from an _application.conf_ file.
 * - Based on the transport implementation defined in the configuration, a message receiver is installed. The
 *   [EndpointHandler] function defined by this instance is invoked for incoming messages.
 * - A dependency injection container is initialized. It already contains some default objects, but derived classes
 *   can add their own modules via the [customModules] function.
 *
 *  Implementations of this [EndpointComponent] can use the shared methods [sleepWhileKeepAliveFileExists] and
 *  [generateKeepAliveFile] to make the pod stay alive after its work is done. This allows manual problem analysis
 *  directly in the pod's execution environment by opening a terminal session.
 */
abstract class EndpointComponent<T : Any>(
    /** The ORT server endpoint implemented by this component. */
    val endpoint: Endpoint<T>,

    /** The configuration for this endpoint wrapped in a [ConfigManager]. */
    val configManager: ConfigManager = ConfigManager.create(ConfigFactory.load())
) : KoinComponent {
    companion object {
        private const val CHECK_INTERVAL_SECONDS = 60L
        private const val KEEP_ALIVE_FILE_NAME = "keep-alive.lock"
        private val logger: Logger = LoggerFactory.getLogger(EndpointComponent::class.java)

        /**
         * Get the keep-alive file in the system's temporary directory.
         */
        private fun getKeepAliveFile(): File {
            val tmpDir = File(System.getProperty("java.io.tmpdir"))
            return tmpDir.resolve(KEEP_ALIVE_FILE_NAME)
        }

        /**
         * Sleep while a keep-alive file exists. This is helpful in case a user terminal session is opened in the
         * Kubernetes pod, and the pod should not terminate immediately after its work is done, giving the user
         * arbitrary extra time for detailed problem analysis directly in the pod's execution environment.
         */
        suspend fun sleepWhileKeepAliveFileExists() {
            val file = getKeepAliveFile()
            while (file.exists()) {
                logger.info(
                    "Delete keep-alive lock file ${file.absolutePath} to continue. " +
                        "Next check in $CHECK_INTERVAL_SECONDS seconds."
                )
                delay(CHECK_INTERVAL_SECONDS * 1000)
            }
        }

        /**
         * Generate a keep-alive file in the user's home directory.
         * See also [sleepWhileKeepAliveFileExists].
         */
        suspend fun generateKeepAliveFile() =
            withContext(Dispatchers.IO) {
                val file = getKeepAliveFile()
                file.createNewFile().let {
                    logger.info("Keep-alive lock file ${file.absolutePath} created.")
            }
        }
    }

    abstract val endpointHandler: EndpointHandler<T>

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Start this endpoint and perform the necessary initialization, so that incoming messages can be received and
     * processed.
     */
    suspend fun start() {
        startKoin {
            modules(baseModule())
            modules(customModules())
        }

        logger.logVersion()
        MessageReceiverFactory.createReceiver(endpoint, configManager, endpointHandler)
    }

    /**
     * Return a list with custom modules for dependency injection that are taken into account when setting up the
     * dependency injection container. This base implementation returns an empty list. Derived classes can override
     * this function to return their own modules.
     */
    protected open fun customModules(): List<Module> = emptyList()

    /**
     * Return a [Module] with singleton objects managed by this base class. This includes a [MessagePublisher] and
     * the global configuration.
     */
    private fun baseModule(): Module = module {
        singleOf(::MessagePublisher)

        single<Config> { configManager }
        single { configManager }
    }
}
