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

package org.ossreviewtoolkit.server.workers.common

import org.ossreviewtoolkit.server.model.Options
import org.ossreviewtoolkit.server.model.PluginConfiguration

/**
 * Type alias for a map with generic options for a worker job.
 */
typealias JobOptions = Map<String, Options>

/**
 * Type alias for a map with [PluginConfiguration] objects for a worker job.
 */
typealias JobPluginOptions = Map<String, PluginConfiguration>

/**
 * An interface allowing the transformation of the options of a worker job.
 *
 * Before a worker can process the options specified in its job configuration, some transformation may be required
 * first. Possible use cases are:
 * - Some options may reference secrets which have to be resolved to their actual values.
 * - Some options may reference configuration files which have to be downloaded.
 *
 * This interface supports such transformations on the (not that trivial) map of job options. A transformation is
 * typically done in multiple steps:
 * 1. A filter function is applied to select the properties in the map which need to be transformed.
 * 2. The actual transformation is applied on the selected properties.
 */
interface OptionsTransformer {
    /**
     * Apply the given [predicate] to the options stored in this object to select the properties that need to be
     * transformed. The function is passed an option value and can decide whether a transformation is required for
     * this value or not. Return an [OptionsTransformer] that is aware of this filter.
     */
    fun filter(predicate: (String) -> Boolean): OptionsTransformer

    /**
     * Apply the given [transformation function][f] to the options stored in this object and return the resulting
     * [JobOptions]. The function is passed all the properties that have been selected by the [filter] function
     * (or all properties if this function has not been called) at once; so, a transformation in parallel could be
     * done if desired.
     */
    suspend fun transform(f: suspend (Set<String>) -> Map<String, String>): JobOptions
}

/**
 * An internal implementation of the [OptionsTransformer] interface.
 */
private class OptionsTransformerImpl(
    /** The [JobOptions] this transformer operates on. */
    private val jobOptions: JobOptions,

    /** The values that have been selected by the filter if any. */
    private val filteredValues: Set<String>? = null
) : OptionsTransformer {
    override fun filter(predicate: (String) -> Boolean): OptionsTransformer {
        val selected = jobOptions.extractValues(predicate)

        return OptionsTransformerImpl(jobOptions, selected)
    }

    override suspend fun transform(f: suspend (Set<String>) -> Map<String, String>): JobOptions {
        val values = filteredValues ?: jobOptions.extractValues()
        if (values.isEmpty()) return jobOptions

        val transformedValues = f(values)

        return jobOptions.mapValues { entry ->
            entry.value.mapValues { transformedValues[it.value] ?: it.value }
        }
    }
}

/**
 * A factory class for creating new [OptionsTransformer] instances.
 */
class OptionsTransformerFactory {
    /**
     * Return a new [OptionsTransformer] that operates on the given [JobOptions].
     */
    fun newTransformer(jobOptions: JobOptions): OptionsTransformer {
        return OptionsTransformerImpl(jobOptions)
    }

    /**
     * Return a new [OptionsTransformer] that operates on the given [JobPluginOptions].
     */
    fun newPluginOptionsTransformer(jobPluginOptions: JobPluginOptions): OptionsTransformer {
        val options = jobPluginOptions.mapValues { entry -> entry.value.options }
        return newTransformer(options)
    }
}

/**
 * Recombines the [PluginConfiguration] objects in this map with the given transformed [options]. This function can be
 * used to obtain modified [PluginConfiguration] objects after the options have been transformed by an
 * [OptionsTransformer]. The resulting configurations have the same secrets, but the options are overridden.
 */
fun JobPluginOptions.recombine(options: JobOptions): JobPluginOptions =
    mapValues { entry ->
        PluginConfiguration(options[entry.key].orEmpty(), entry.value.secrets)
    }

/**
 * Extract all the values stored in this [JobOptions] object that match the given [predicate].
 */
private fun JobOptions.extractValues(predicate: (String) -> Boolean = { true }): Set<String> =
    flatMapTo(mutableSetOf()) { e -> e.value.values.filter(predicate) }
