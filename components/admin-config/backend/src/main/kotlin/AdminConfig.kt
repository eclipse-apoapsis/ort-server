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

import org.ossreviewtoolkit.utils.ort.ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME

/**
 * A class representing the admin configuration for the ORT server. It defines an object model for the configuration
 * file loaded from the config file provider.
 */
class AdminConfig(
    /** The configuration for the Scanner worker. */
    val scannerConfig: ScannerConfig = ScannerConfig(),

    /** The configuration for the Reporter worker. */
    val reporterConfig: ReporterConfig = DEFAULT_REPORTER_CONFIG,

    /** The configuration for the Notifier worker. */
    val notifierConfig: NotifierConfig = NotifierConfig(),

    /** The default rule set. */
    private val defaultRuleSet: RuleSet = RuleSet(),

    /**
     *  A map containing named rule set templates. Any `null` properties of the templates will be replaced with the
     *  respective values from the [defaultRuleSet].
     */
    ruleSets: Map<String, RuleSetTemplate> = emptyMap(),

    /** The global mirror for Maven Central. */
    val mavenCentralMirror: MavenCentralMirror? = null
) {
    companion object {
        /**
         * A default [ReporterConfig] instance that is used if the admin configuration does not contain any
         * reporter-specific settings. This instance is not really useful, however, since it does not support any
         * reports. Typically, some reports should be configured.
         */
        val DEFAULT_REPORTER_CONFIG = ReporterConfig(
            reportDefinitionsMap = ReporterConfig.addDefinitionsForUnreferencedPlugins(emptyMap()),
            howToFixTextProviderFile = ORT_HOW_TO_FIX_TEXT_PROVIDER_FILENAME,
            customLicenseTextDir = null
        )

        /**
         * An empty default configuration. This is going to be used if no path to a configuration file is specified,
         * and the default path does not exist.
         */
        val DEFAULT = AdminConfig()
    }

    private val ruleSets: Map<String, RuleSet> = ruleSets.mapValues { (_, template) ->
        RuleSet(
            copyrightGarbageFile = template.copyrightGarbageFile ?: defaultRuleSet.copyrightGarbageFile,
            licenseClassificationsFile = template.licenseClassificationsFile
                ?: defaultRuleSet.licenseClassificationsFile,
            resolutionsFile = template.resolutionsFile ?: defaultRuleSet.resolutionsFile,
            evaluatorRules = template.evaluatorRules ?: defaultRuleSet.evaluatorRules
        )
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
