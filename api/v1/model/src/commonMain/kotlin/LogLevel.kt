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

package org.eclipse.apoapsis.ortserver.api.v1.model

/**
 * An enumeration class defining the supported log levels in ORT Server log files.
 *
 * Using the constants provided by this class, users can filter for specific log information when downloading log
 * files.
 */
enum class LogLevel {
    /** The log level DEBUG. */
    DEBUG,

    /** The log level INFO. */
    INFO,

    /** The log level WARN. */
    WARN,

    /** The log level ERROR. */
    ERROR
}
