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

import io.ktor.server.config.ApplicationConfig

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

import org.koin.dsl.module

import org.ossreviewtoolkit.server.core.client.KeycloakService
import org.ossreviewtoolkit.server.core.plugins.customSerializersModule
import org.ossreviewtoolkit.server.dao.repositories.DaoOrganizationRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoProductRepository
import org.ossreviewtoolkit.server.dao.repositories.DaoRepositoryRepository
import org.ossreviewtoolkit.server.model.repositories.OrganizationRepository
import org.ossreviewtoolkit.server.model.repositories.ProductRepository
import org.ossreviewtoolkit.server.model.repositories.RepositoryRepository
import org.ossreviewtoolkit.server.services.OrganizationService
import org.ossreviewtoolkit.server.services.ProductService
import org.ossreviewtoolkit.server.services.RepositoryService

@OptIn(ExperimentalSerializationApi::class)
fun ortServerModule(config: ApplicationConfig) = module {
    single { config }

    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            serializersModule = customSerializersModule
        }
    }

    single { KeycloakService.create(get(), get()) }

    single<OrganizationRepository> { DaoOrganizationRepository() }
    single<ProductRepository> { DaoProductRepository() }
    single<RepositoryRepository> { DaoRepositoryRepository() }

    single { OrganizationService(get(), get()) }
    single { ProductService(get(), get()) }
    single { RepositoryService(get()) }
}
