/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.dao.repositories

import kotlinx.datetime.Instant

import org.ossreviewtoolkit.server.dao.blockingQuery
import org.ossreviewtoolkit.server.dao.tables.AnalyzerJobDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.AnalyzerConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.AnalyzerRunDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.AnalyzerRunsTable
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.PackageManagerConfigurationDao
import org.ossreviewtoolkit.server.dao.tables.runs.analyzer.PackageManagerConfigurationOptionDao
import org.ossreviewtoolkit.server.dao.tables.runs.shared.EnvironmentDao
import org.ossreviewtoolkit.server.model.repositories.AnalyzerRunRepository
import org.ossreviewtoolkit.server.model.runs.AnalyzerConfiguration
import org.ossreviewtoolkit.server.model.runs.AnalyzerRun
import org.ossreviewtoolkit.server.model.runs.CuratedPackage
import org.ossreviewtoolkit.server.model.runs.Identifier
import org.ossreviewtoolkit.server.model.runs.OrtIssue
import org.ossreviewtoolkit.server.model.runs.Project

/**
 * An implementation of [AnalyzerRunRepository] that stores analyzer runs in [AnalyzerRunsTable].
 */
class DaoAnalyzerRunRepository : AnalyzerRunRepository {
    override fun create(
        analyzerJobId: Long,
        environmentId: Long,
        startTime: Instant,
        endTime: Instant,
        config: AnalyzerConfiguration,
        projects: Set<Project>,
        packages: Set<CuratedPackage>,
        issues: Map<Identifier, List<OrtIssue>>
    ): AnalyzerRun = blockingQuery {
        val analyzerRun = AnalyzerRunDao.new {
            this.analyzerJob = AnalyzerJobDao[analyzerJobId]
            this.startTime = startTime
            this.endTime = endTime
            this.environment = EnvironmentDao[environmentId]
        }

        createAnalyzerConfiguration(analyzerRun, config)

        // TODO: Create projects, packages, and issues.

        analyzerRun.mapToModel()
    }.getOrThrow()

    override fun get(id: Long): AnalyzerRun? = blockingQuery { AnalyzerRunDao[id].mapToModel() }.getOrNull()
}

private fun createAnalyzerConfiguration(
    analyzerRun: AnalyzerRunDao,
    analyzerConfiguration: AnalyzerConfiguration
): AnalyzerConfigurationDao {
    val analyzerConfigurationDao = AnalyzerConfigurationDao.new {
        this.analyzerRun = analyzerRun
        allowDynamicVersions = analyzerConfiguration.allowDynamicVersions
        enabledPackageManagers = analyzerConfiguration.enabledPackageManagers
        disabledPackageManagers = analyzerConfiguration.disabledPackageManagers
    }

    analyzerConfiguration.packageManagers?.forEach { (packageManager, packageManagerConfiguration) ->
        val packageManagerConfigurationDao = PackageManagerConfigurationDao.new {
            this.analyzerConfiguration = analyzerConfigurationDao
            name = packageManager
            mustRunAfter = packageManagerConfiguration.mustRunAfter
        }

        packageManagerConfiguration.options?.forEach { (name, value) ->
            PackageManagerConfigurationOptionDao.new {
                this.packageManagerConfiguration = packageManagerConfigurationDao
                this.name = name
                this.value = value
            }
        }
    }

    return analyzerConfigurationDao
}
