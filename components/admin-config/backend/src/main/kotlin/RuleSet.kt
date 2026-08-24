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

package org.eclipse.apoapsis.ortserver.components.adminconfig

import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_EVALUATOR_RULES_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

/**
 * A data class that represents the configuration of a rule set.
 *
 * A rule set consists of a number of paths to files that are used by the Evaluator and partly by the Reporter. The
 * paths are passed to the [config file provider][org.eclipse.apoapsis.ortserver.config.ConfigFileProvider] to obtain
 * the actual configuration files. Interpretation of the paths is up to the provider, but for file-based providers paths
 * can be relative to the provider's root directory.
 *
 * By default, all properties are initialized with the standard paths from ORT.
 */
data class RuleSet(
    /** The path to the copyright garbage file. */
    val copyrightGarbageFile: String = DEFAULT_COPYRIGHT_GARBAGE_FILE,

    /** The path to the license classifications file. */
    val licenseClassificationsFile: String = DEFAULT_LICENSE_CLASSIFICATIONS_FILE,

    /** The path to the resolutions file. */
    val resolutionsFile: String = DEFAULT_RESOLUTIONS_FILE,

    /** The path to the files with rules to use for the evaluation. */
    val evaluatorRules: String = DEFAULT_EVALUATOR_RULES_FILE
) {
    companion object {
        internal const val DEFAULT_COPYRIGHT_GARBAGE_FILE = ORT_COPYRIGHT_GARBAGE_FILENAME
        internal const val DEFAULT_LICENSE_CLASSIFICATIONS_FILE = ORT_LICENSE_CLASSIFICATIONS_FILENAME
        internal const val DEFAULT_RESOLUTIONS_FILE = ORT_RESOLUTIONS_FILENAME
        internal const val DEFAULT_EVALUATOR_RULES_FILE = ORT_EVALUATOR_RULES_FILENAME
    }

    /** Return a set of all non-default config files referenced by this rule set. */
    fun getNonDefaultConfigFiles() =
        setOfNotNull(
            copyrightGarbageFile.takeIf { it != DEFAULT_COPYRIGHT_GARBAGE_FILE },
            licenseClassificationsFile.takeIf { it != DEFAULT_LICENSE_CLASSIFICATIONS_FILE },
            resolutionsFile.takeIf { it != DEFAULT_RESOLUTIONS_FILE },
            evaluatorRules.takeIf { it != DEFAULT_EVALUATOR_RULES_FILE }
        )
}

/** A template for a [RuleSet] where all properties are nullable. */
data class RuleSetTemplate(
    val copyrightGarbageFile: String? = null,
    val licenseClassificationsFile: String? = null,
    val resolutionsFile: String? = null,
    val evaluatorRules: String? = null
)
