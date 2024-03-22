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

package org.eclipse.apoapsis.ortserver.workers.common

import org.eclipse.apoapsis.ortserver.dao.repositories.DaoAdvisorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoAdvisorRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoAnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoAnalyzerRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoEvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoEvaluatorRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoNotifierJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoReporterJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoReporterRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoRepositoryConfigurationRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoRepositoryRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoResolvedConfigurationRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoScannerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.DaoScannerRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryConfigurationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ResolvedConfigurationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerRunRepository

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Return a module with bean definitions for the [OrtRunService] and the repositories it depends on.
 */
fun ortRunServiceModule(): Module = module {
    single<AdvisorJobRepository> { DaoAdvisorJobRepository(get()) }
    single<AdvisorRunRepository> { DaoAdvisorRunRepository(get()) }
    single<AnalyzerJobRepository> { DaoAnalyzerJobRepository(get()) }
    single<AnalyzerRunRepository> { DaoAnalyzerRunRepository(get()) }
    single<EvaluatorJobRepository> { DaoEvaluatorJobRepository(get()) }
    single<EvaluatorRunRepository> { DaoEvaluatorRunRepository(get()) }
    single<OrtRunRepository> { DaoOrtRunRepository(get()) }
    single<ReporterJobRepository> { DaoReporterJobRepository(get()) }
    single<ReporterRunRepository> { DaoReporterRunRepository(get()) }
    single<NotifierJobRepository> { DaoNotifierJobRepository(get()) }
    single<RepositoryConfigurationRepository> { DaoRepositoryConfigurationRepository(get()) }
    single<RepositoryRepository> { DaoRepositoryRepository(get()) }
    single<ResolvedConfigurationRepository> { DaoResolvedConfigurationRepository(get()) }
    single<ScannerJobRepository> { DaoScannerJobRepository(get()) }
    single<ScannerRunRepository> { DaoScannerRunRepository(get()) }

    singleOf(::OrtRunService)
}
