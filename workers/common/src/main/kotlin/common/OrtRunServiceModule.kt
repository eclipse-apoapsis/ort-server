/*
 * Copyright (C) 2023 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoEvaluatorJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoEvaluatorRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrtRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoReporterJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoReporterRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryConfigurationRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoResolvedConfigurationRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoScannerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoScannerRunRepository
import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AdvisorRunRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerRunRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorJobRepository
import org.ossreviewtoolkit.server.model.repositories.EvaluatorRunRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.ReporterJobRepository
import org.ossreviewtoolkit.server.model.repositories.ReporterRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryConfigurationRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.repositories.ResolvedConfigurationRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerJobRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerRunRepository

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
    single<RepositoryConfigurationRepository> { DaoRepositoryConfigurationRepository(get()) }
    single<RepositoryRepository> { DaoRepositoryRepository(get()) }
    single<ResolvedConfigurationRepository> { DaoResolvedConfigurationRepository(get()) }
    single<ScannerJobRepository> { DaoScannerJobRepository(get()) }
    single<ScannerRunRepository> { DaoScannerRunRepository(get()) }

    singleOf(::OrtRunService)
}
