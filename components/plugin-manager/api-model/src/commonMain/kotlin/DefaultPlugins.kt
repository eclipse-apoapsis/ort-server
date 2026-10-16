/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.components.pluginmanager

import kotlinx.serialization.Serializable

/**
 * The categories for which default plugins can be configured. Each category refers to the plugins of a [pluginType].
 * Usually, there is one category per plugin type, but scanners can be configured separately for scanning packages and
 * projects.
 */
enum class DefaultPluginsCategory(
    /** The type of the plugins in this category. */
    val pluginType: PluginType
) {
    ADVISOR(PluginType.ADVISOR),
    PACKAGE_CONFIGURATION_PROVIDER(PluginType.PACKAGE_CONFIGURATION_PROVIDER),
    PACKAGE_CURATION_PROVIDER(PluginType.PACKAGE_CURATION_PROVIDER),
    PACKAGE_MANAGER(PluginType.PACKAGE_MANAGER),

    /** The scanners used to scan projects. If not configured, projects are scanned with the [SCANNER]s. */
    PROJECT_SCANNER(PluginType.SCANNER),
    REPORTER(PluginType.REPORTER),

    /** The scanners used to scan packages. */
    SCANNER(PluginType.SCANNER)
}

/**
 * The plugins of a specific [category] that are enabled by default if a run does not explicitly configure which
 * plugins to use.
 */
@Serializable
data class DefaultPlugins(
    /** The category of the plugins. */
    val category: DefaultPluginsCategory,

    /**
     * The IDs of the plugins enabled by default. The order is relevant for categories where the order of the plugins
     * matters, like package curation providers and package configuration providers.
     */
    val pluginIds: List<String>,

    /** The source the default plugins were taken from. */
    val source: DefaultPluginsSource
)

/**
 * The source of [DefaultPlugins].
 */
enum class DefaultPluginsSource {
    /** The built-in defaults are used, because no defaults were configured. */
    BUILT_IN,

    /** The defaults configured globally by the server administrators are used. */
    GLOBAL,

    /** The defaults configured for the organization are used. */
    ORGANIZATION
}
