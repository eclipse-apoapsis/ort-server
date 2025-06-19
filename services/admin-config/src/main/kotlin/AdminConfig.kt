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

import org.ossreviewtoolkit.utils.ort.ORT_COPYRIGHT_GARBAGE_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_EVALUATOR_RULES_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_LICENSE_CLASSIFICATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME

/**
 * A class representing the admin configuration for the ORT server. It defines an object model for the configuration
 * file loaded from the config file provider.
 */
class AdminConfig(
    /** The default rule set. */
    private val defaultRuleSet: RuleSet = DEFAULT_RULE_SET,

    /** A map containing named rule sets. */
    private val ruleSets: Map<String, RuleSet> = emptyMap()
) {
    companion object {
        /**
         * A default [RuleSet] instance that uses the standard names from ORT for the referenced files.
         */
        val DEFAULT_RULE_SET = RuleSet(
            copyrightGarbageFile = ORT_COPYRIGHT_GARBAGE_FILENAME,
            licenseClassificationsFile = ORT_LICENSE_CLASSIFICATIONS_FILENAME,
            resolutionsFile = ORT_RESOLUTIONS_FILENAME,
            evaluatorRules = ORT_EVALUATOR_RULES_FILENAME
        )

        /**
         * An empty default configuration. This is going to be used if no path to a configuration file is specified,
         * and the default path does not exist.
         */
        val DEFAULT = AdminConfig()
    }

    /**
     * Return a set with the names of all defined rule sets. These names can be passed to the [getRuleSet] function to
     * obtain the corresponding [RuleSet] instance. In addition, the name *null* can be used to obtain the default
     * rule set.
     */
    val ruleSetNames: Set<String>
        get() = ruleSets.keys

    /**
     * Return the [RuleSet] with the given [name]. A *null* name returns the default rule set. All other names refer
     * to a named rule set which must be defined in the configuration file; otherwise, this function throws an
     * exception.
     */
    fun getRuleSet(name: String?): RuleSet =
        if (name == null) {
            defaultRuleSet
        } else {
            ruleSets[name] ?: throw NoSuchElementException("No rule set defined with the name '$name'.")
        }
}
