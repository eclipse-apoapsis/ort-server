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

package org.eclipse.apoapsis.ortserver.components.pluginmanager

import org.ossreviewtoolkit.advisor.AdviceProviderFactory
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.api.PackageConfigurationProviderFactory
import org.ossreviewtoolkit.plugins.packagecurationproviders.api.PackageCurationProviderFactory
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory

/**
 * Returns all installed plugins for the given [pluginType].
 */
internal fun getInstalledPlugins(pluginType: PluginType): List<PluginDescriptor> =
    when (pluginType) {
        PluginType.ADVISOR -> AdviceProviderFactory.ALL.values.map { it.descriptor }
        PluginType.PACKAGE_CONFIGURATION_PROVIDER ->
            PackageConfigurationProviderFactory.ALL.values.map { it.descriptor }
        PluginType.PACKAGE_CURATION_PROVIDER -> PackageCurationProviderFactory.ALL.values.map { it.descriptor }
        PluginType.PACKAGE_MANAGER -> PackageManagerFactory.ALL.values.map { it.descriptor }
        PluginType.REPORTER -> ReporterFactory.ALL.values.map { it.descriptor }
        PluginType.SCANNER -> ScannerWrapperFactory.ALL.values.map { it.descriptor }
    }
