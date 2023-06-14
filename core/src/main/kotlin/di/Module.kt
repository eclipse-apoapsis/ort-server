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

package org.ossreviewtoolkit.server.core.di

import com.typesafe.config.ConfigFactory

import io.ktor.server.config.ApplicationConfig

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.ossreviewtoolkit.server.clients.keycloak.DefaultKeycloakClient
import org.ossreviewtoolkit.server.clients.keycloak.KeycloakClient
import org.ossreviewtoolkit.server.core.plugins.customSerializersModule
import org.ossreviewtoolkit.server.core.services.OrchestratorService
import org.ossreviewtoolkit.server.core.utils.createKeycloakClientConfiguration
import org.ossreviewtoolkit.server.dao.repositories.DaoAdvisorJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoAnalyzerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoInfrastructureServiceRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrganizationRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoOrtRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoProductRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoReporterRunRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoScannerJobRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoSecretRepository
import org.ossreviewtoolkit.server.model.repositories.AdvisorJobRepository
import org.ossreviewtoolkit.server.model.repositories.AnalyzerJobRepository
import org.ossreviewtoolkit.server.model.repositories.InfrastructureServiceRepository
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.OrtRunRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.repositories.ReporterRunRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.model.repositories.ScannerJobRepository
import org.ossreviewtoolkit.server.model.repositories.SecretRepository
import org.ossreviewtoolkit.server.secrets.SecretStorage
import org.ossreviewtoolkit.server.services.AuthorizationService
import org.ossreviewtoolkit.server.services.DefaultAuthorizationService
import org.ossreviewtoolkit.server.services.InfrastructureServiceService
import org.ossreviewtoolkit.server.services.OrganizationService
import org.ossreviewtoolkit.server.services.ProductService
import org.ossreviewtoolkit.server.services.ReportStorageService
import org.ossreviewtoolkit.server.services.RepositoryService
import org.ossreviewtoolkit.server.services.SecretService
import org.ossreviewtoolkit.server.storage.Storage

@OptIn(ExperimentalSerializationApi::class)
fun ortServerModule(config: ApplicationConfig) = module {
    single { config }
    single { ConfigFactory.parseMap(config.toMap()) }

    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            serializersModule = customSerializersModule
        }
    }

    single<KeycloakClient> {
        DefaultKeycloakClient.create(get<ApplicationConfig>().createKeycloakClientConfiguration(), get())
    }

    single<AdvisorJobRepository> { DaoAdvisorJobRepository(get()) }
    single<ScannerJobRepository> { DaoScannerJobRepository(get()) }
    single<AnalyzerJobRepository> { DaoAnalyzerJobRepository(get()) }
    single<OrganizationRepository> { DaoOrganizationRepository(get()) }
    single<OrtRunRepository> { DaoOrtRunRepository(get()) }
    single<ProductRepository> { DaoProductRepository(get()) }
    single<RepositoryRepository> { DaoRepositoryRepository(get()) }
    single<ReporterRunRepository> { DaoReporterRunRepository(get()) }
    single<SecretRepository> { DaoSecretRepository(get()) }
    single<InfrastructureServiceRepository> { DaoInfrastructureServiceRepository(get()) }

    single { SecretStorage.createStorage(get()) }
    single { Storage.create("reportStorage", get()) }

    single<AuthorizationService> { DefaultAuthorizationService(get(), get(), get(), get(), get()) }
    single { OrchestratorService(get(), get(), get()) }
    single { OrganizationService(get(), get(), get(), get()) }
    single { ProductService(get(), get(), get(), get()) }
    single { RepositoryService(get(), get(), get(), get()) }
    single { SecretService(get(), get(), get()) }
    singleOf(::ReportStorageService)
    singleOf(::InfrastructureServiceService)
}
