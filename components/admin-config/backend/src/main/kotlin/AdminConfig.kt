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

import com.sksamuel.hoplite.ConfigAlias

/**
 * A class representing the admin configuration for the ORT server. It defines an object model for the configuration
 * file loaded from the config file provider.
 */
data class AdminConfig(
    /** The configuration for the Scanner worker. */
    @param:ConfigAlias("scanner")
    val scannerConfig: ScannerConfig = ScannerConfig(),

    /** The configuration for the Reporter worker. */
    @param:ConfigAlias("reporter")
    val reporterConfig: ReporterConfig = ReporterConfig(),

    /** The configuration for the Notifier worker. */
    @param:ConfigAlias("notifier")
    val notifierConfig: NotifierConfig = NotifierConfig(),

    /** The default rule set. */
    private val defaultRuleSet: RuleSet = RuleSet(),

    /**
     *  A map containing named rule set templates. Any `null` properties of the templates will be replaced with the
     *  respective values from the [defaultRuleSet].
     */
    private val ruleSets: Map<String, RuleSetTemplate> = emptyMap(),

    /** The global mirror for Maven Central. */
    val mavenCentralMirror: MavenCentralMirror? = null
) {
    companion object {
        /**
         * An empty default configuration. This is going to be used if no path to a configuration file is specified,
         * and the default path does not exist.
         */
        val DEFAULT = AdminConfig()
    }

    private val resolvedRuleSets: Map<String, RuleSet> = ruleSets.mapValues { (_, template) ->
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
        get() = resolvedRuleSets.keys

    /**
     * Return the [RuleSet] with the given [name]. A *null* name returns the default rule set. All other names refer
     * to a named rule set which must be defined in the configuration file; otherwise, this function throws an
     * exception.
     */
    fun getRuleSet(name: String?): RuleSet =
        if (name == null) {
            defaultRuleSet
        } else {
            resolvedRuleSets[name] ?: throw NoSuchElementException("No rule set defined with the name '$name'.")
        }

    /** Return a set of all non-default config files referenced by this config. */
    fun getNonDefaultConfigFiles(): Set<String> = buildSet {
        addAll(defaultRuleSet.getNonDefaultConfigFiles())

        resolvedRuleSets.forEach { (_, ruleSet) ->
            addAll(ruleSet.getNonDefaultConfigFiles())
        }

        addAll(reporterConfig.getNonDefaultConfigFiles())
    }
}
