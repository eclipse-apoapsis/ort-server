/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.workers.common.env

import org.eclipse.apoapsis.ortserver.model.InfrastructureService

import org.slf4j.LoggerFactory

/**
 * Logger helper class for the environment configuration generators.
 * It helps log messages related to the generation of environment configuration files.
 */
internal object GeneratorLogger {

    private val logger = LoggerFactory.getLogger(GeneratorLogger::class.java)

    internal fun entryAdded(entry: String, targetFile: String, service: InfrastructureService) {
        logger.debug(
            "Added entry '{}' to '{}' file for '{}', '{}', '{}'.",
            entry,
            targetFile,
            service.name,
            service.organization?.name.orEmpty(),
            service.product?.name.orEmpty()
        )
    }

    internal fun entryAdded(entry: String, targetFile: String) {
        logger.debug("Added entry '{}' to '{}' file.", entry, targetFile)
    }

    internal fun proxySettingAdded(entry: String, targetFile: String) {
        logger.debug("Added proxy setting '{}' to {} file.", entry, targetFile)
    }

    internal fun error(errorMsg: String, targetFile: String, exception: Throwable) {
        logger.error("Error occurred while generating '{}': \n{}\n", targetFile, errorMsg, exception)
    }
}
