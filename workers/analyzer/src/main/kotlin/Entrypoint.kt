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

package org.eclipse.apoapsis.ortserver.workers.analyzer

import org.eclipse.apoapsis.ortserver.utils.logging.StandardMdcKeys
import org.eclipse.apoapsis.ortserver.utils.logging.withMdcContext
import org.eclipse.apoapsis.ortserver.workers.common.enableOrtStackTraces

import org.ossreviewtoolkit.utils.common.Os

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(AnalyzerComponent::class.java)

/**
 * This is the entry point of the Analyzer worker. It calls the Analyzer from ORT programmatically by
 * interfacing on its APIs.
 *
 * Per default, a whole analysis is performed. This can be changed via command line arguments. The first argument is
 * interpreted as the name of the analyzer phase to execute; further arguments are then passed to the phase. The
 * following phases are supported (names are case-insensitive):
 * - _preparation_ for the preparation phase
 * - _analysis_ for the analysis phase
 * - _result_ for the result phase
 * - The phase _full_ can be exlicitly specified to run the whole analysis. This has the same effect as providing no
 *   argument at all.
 */
suspend fun main(args: Array<String>) {
    withMdcContext(StandardMdcKeys.COMPONENT to "analyzer-worker") {
        logger.info("Starting ORT-Server Analyzer endpoint with arguments {}.", args.contentToString())

        enableOrtStackTraces()
        Os.fixupUserHomeProperty()
        AnalyzerComponent(args).start()
    }
}
