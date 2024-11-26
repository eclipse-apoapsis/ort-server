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

package org.eclipse.apoapsis.ortserver.workers.common.env

import org.eclipse.apoapsis.ortserver.dao.repositories.infrastructureservice.DaoInfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.dao.repositories.secret.DaoSecretRepository
import org.eclipse.apoapsis.ortserver.model.repositories.InfrastructureServiceRepository
import org.eclipse.apoapsis.ortserver.model.repositories.SecretRepository
import org.eclipse.apoapsis.ortserver.workers.common.env.config.EnvironmentConfigLoader
import org.eclipse.apoapsis.ortserver.workers.common.env.config.EnvironmentDefinitionFactory

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Return a [Module] with bean definitions that provide an [EnvironmentService] instance and its dependencies. This
 * module can be used by worker implementations that need to set up a build environment.
 */
fun buildEnvironmentModule(): Module = module {
    single<InfrastructureServiceRepository> { DaoInfrastructureServiceRepository(get()) }
    single<SecretRepository> { DaoSecretRepository(get()) }

    singleOf(::EnvironmentDefinitionFactory)
    singleOf(::EnvironmentConfigLoader)

    single {
        EnvironmentService(
            get(),
            listOf(
                ConanGenerator(),
                GitCredentialsGenerator(),
                MavenSettingsGenerator(),
                NetRcGenerator(),
                NpmRcGenerator(),
                NuGetGenerator(),
                YarnRcGenerator()
            ),
            get()
        )
    }
}
