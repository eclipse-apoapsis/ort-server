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

package org.eclipse.apoapsis.ortserver.workers.config

import org.eclipse.apoapsis.ortserver.utils.logging.StandardMdcKeys
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(ConfigComponent::class.java)

/**
 * This is the entry point for the Config worker. It is a bit special, since there is no direct counterpart in ORT.
 * The worker checks and transforms the configuration/parameters passed to the current ORT run using a validation
 * script.
 */
suspend fun main() {
    withMdcContext(StandardMdcKeys.COMPONENT to "config-worker") {
        logger.info("Starting ORT Server Config endpoint.")

        ConfigComponent().start()
    }
}
