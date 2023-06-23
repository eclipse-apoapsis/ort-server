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

package org.ossreviewtoolkit.server.workers.common.env.definition

import org.ossreviewtoolkit.server.model.InfrastructureService

/**
 * A specific [EnvironmentServiceDefinition] class for generating the Maven _settings.xml_ file.
 *
 * This class defines under which ID a specific [InfrastructureService] with its credentials should be referenced from
 * the _settings.xml_ file.
 */
class MavenDefinition(
    service: InfrastructureService,

    /**
     * The ID of the represented service. This ID is used in _pom.xml_ files to refer to the represented repository.
     * In the generated _settings.xml_ file, the ID appears as the _id_ property for the corresponding server in the
     * _servers_ section. See https://maven.apache.org/settings.html#servers.
     */
    val id: String
) : EnvironmentServiceDefinition(service)
