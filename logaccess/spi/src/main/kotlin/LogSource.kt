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

package org.eclipse.apoapsis.ortserver.logaccess

/**
 * An enumeration class that defines the different sources for which log files can be downloaded. The sources
 * available correspond to the single workers executed during an ORT run.
 */
enum class LogSource(
    /** The value used for the component entry in the MDC. */
    val component: String
) {
    /** Log source for the Config worker. */
    CONFIG("config-worker"),

    /** Log source for the Analyzer worker. */
    ANALYZER("analyzer-worker"),

    /** Log source for the Advisor worker. */
    ADVISOR("advisor-worker"),

    /** Log source for the Scanner worker. */
    SCANNER("scanner-worker"),

    /** Log source for the Evaluator worker. */
    EVALUATOR("evaluator-worker"),

    /** Log source for the Reporter worker. */
    REPORTER("reporter-worker"),

    /** Log source for the Notifier worker. */
    NOTIFIER("notifier-worker")
}
