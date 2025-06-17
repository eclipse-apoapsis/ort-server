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

package org.eclipse.apoapsis.ortserver.services.config

/**
 * A data class that represents the configuration of a rule set.
 *
 * A rule set consists of a number of paths to files that are used by the Evaluator and partly by the Reporter. The
 * paths are passed to the _config file provider_ to obtain the actual configuration files.
 */
data class RuleSet(
    /** The path to the copyright garbage file. */
    val copyrightGarbageFile: String,

    /** The path to the license classifications file. */
    val licenseClassificationsFile: String,

    /** The path to the resolutions file. */
    val resolutionsFile: String,

    /** The path to the files with rules to use for the evaluation. */
    val evaluatorRules: String
)
