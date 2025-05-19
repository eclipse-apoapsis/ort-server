/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.di

import com.typesafe.config.ConfigFactory

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString

import kotlinx.serialization.json.Json

import org.eclipse.apoapsis.ortserver.clients.keycloak.DefaultKeycloakClient
import org.eclipse.apoapsis.ortserver.clients.keycloak.KeycloakClient
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginEventStore
import org.eclipse.apoapsis.ortserver.components.pluginmanager.PluginService
import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.core.plugins.customSerializersModule
import org.eclipse.apoapsis.ortserver.core.services.OrchestratorService
import org.eclipse.apoapsis.ortserver.core.utils.createKeycloakClientConfiguration
import org.eclipse.apoapsis.ortserver.dao.DataSourceConfig
import org.eclipse.apoapsis.ortserver.dao.connect
import org.eclipse.apoapsis.ortserver.dao.createDataSource
import org.eclipse.apoapsis.ortserver.dao.migrate
import org.eclipse.apoapsis.ortserver.dao.repositories.advisorjob.DaoAdvisorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.analyzerjob.DaoAnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.evaluatorjob.DaoEvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.infrastructureservice.DaoInfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierjob.DaoNotifierJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.notifierrun.DaoNotifierRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.organization.DaoOrganizationRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.ortrun.DaoOrtRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.product.DaoProductRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterjob.DaoReporterJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.reporterrun.DaoReporterRunRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.repository.DaoRepositoryRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.scannerjob.DaoScannerJobRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.DaoSecretRepository
import org.eclipse.apoapsis.ortserver.logaccess.LogFileService
import org.eclipse.apoapsis.ortserver.model.repositories.AdvisorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.AnalyzerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.EvaluatorJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.NotifierRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrganizationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.OrtRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ProductRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ReporterRunRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository
import org.eclipse.apoapsis.ortserver.model.repositories.ScannerJobRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.secrets.SecretStorage
import org.eclipse.apoapsis.ortserver.services.AuthorizationService
import org.eclipse.apoapsis.ortserver.services.ContentManagementService
import org.eclipse.apoapsis.ortserver.services.DefaultAuthorizationService
import org.eclipse.apoapsis.ortserver.services.InfrastructureServiceService
import org.eclipse.apoapsis.ortserver.services.IssueService
import org.eclipse.apoapsis.ortserver.services.OrganizationService
import org.eclipse.apoapsis.ortserver.services.OrtRunService
import org.eclipse.apoapsis.ortserver.services.PackageService
import org.eclipse.apoapsis.ortserver.services.ProductService
import org.eclipse.apoapsis.ortserver.services.ProjectService
import org.eclipse.apoapsis.ortserver.services.ReportStorageService
import org.eclipse.apoapsis.ortserver.services.RepositoryService
import org.eclipse.apoapsis.ortserver.services.RuleViolationService
import org.eclipse.apoapsis.ortserver.services.SecretService
import org.eclipse.apoapsis.ortserver.services.UserService
import org.eclipse.apoapsis.ortserver.services.VulnerabilityService
import org.eclipse.apoapsis.ortserver.storage.Storage

import org.jetbrains.exposed.sql.Database

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Creates the Koin module for the ORT server. The [config] is used to configure the application and the database. For
 * integration tests, the [database][db] from the testcontainer can be provided directly.
 */
fun ortServerModule(config: ApplicationConfig, db: Database?) = module {
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
        DefaultKeycloakClient.create(get<ConfigManager>().createKeycloakClientConfiguration(), get())
    }

    if (db != null) {
        single<Database> { db }
    } else {
        single<Database>(createdAtStart = true) {
            val configManager = get<ConfigManager>()
            val dataSourceConfig = DataSourceConfig.create(configManager)
            val dataSource = createDataSource(dataSourceConfig)
            dataSource.connect().also {
                // This is the only place where migrations for the database are done. While other services can connect
                // to the database, they must not handle migrations.
                dataSource.migrate()
            }
        }
    }

    single<AdvisorJobRepository> { DaoAdvisorJobRepository(get()) }
    single<AnalyzerJobRepository> { DaoAnalyzerJobRepository(get()) }
    single<EvaluatorJobRepository> { DaoEvaluatorJobRepository(get()) }
    single<ReporterJobRepository> { DaoReporterJobRepository(get()) }
    single<NotifierJobRepository> { DaoNotifierJobRepository(get()) }
    single<ScannerJobRepository> { DaoScannerJobRepository(get()) }
    single<OrganizationRepository> { DaoOrganizationRepository(get()) }
    single<OrtRunRepository> { DaoOrtRunRepository(get()) }
    single<ProductRepository> { DaoProductRepository(get()) }
    single<RepositoryRepository> { DaoRepositoryRepository(get()) }
    single<ReporterRunRepository> { DaoReporterRunRepository(get()) }
    single<NotifierRunRepository> { DaoNotifierRunRepository(get()) }
    single<SecretRepository> { DaoSecretRepository(get()) }
    single<InfrastructureServiceRepository> { DaoInfrastructureServiceRepository(get()) }

    single { SecretStorage.createStorage(get()) }
    single { ConfigManager.create(get()) }
    single { LogFileService.create(get()) }

    single<AuthorizationService> {
        val keycloakGroupPrefix = get<ApplicationConfig>().tryGetString("keycloak.groupPrefix").orEmpty()
        DefaultAuthorizationService(get(), get(), get(), get(), get(), keycloakGroupPrefix)
    }
    single { OrchestratorService(get(), get(), get()) }
    single { OrganizationService(get(), get(), get(), get()) }
    single { ProductService(get(), get(), get(), get(), get()) }
    single { RepositoryService(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { SecretService(get(), get(), get(), get()) }
    single { VulnerabilityService(get()) }
    single { IssueService(get()) }
    single { RuleViolationService(get()) }
    single { PackageService(get()) }
    single { ProjectService(get()) }
    single { UserService(get()) }
    single { OrtRunService(get(), get(), get(), get()) }
    single { ContentManagementService(get()) }
    single {
        val storage = Storage.create("reportStorage", get())
        ReportStorageService(storage, get())
    }
    singleOf(::InfrastructureServiceService)

    single { PluginEventStore(get()) }
    single { PluginService(get()) }
}
