/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.transport

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import org.koin.core.component.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

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
 */
abstract class EndpointComponent<T : Any>(
    /** The ORT server endpoint implemented by this component. */
    val endpoint: Endpoint<T>,

    /** The configuration for this endpoint. */
    val config: Config = ConfigFactory.load()
    ) : KoinComponent {
    abstract val endpointHandler: EndpointHandler<T>

    /**
     * Start this endpoint and perform necessary initialization, so that incoming messages can be received and
     * processed.
     */
    fun start() {
        startKoin {
            modules(baseModule())
            modules(customModules())
        }

        MessageReceiverFactory.createReceiver(endpoint, config, endpointHandler)
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

        single { config }
    }
}
