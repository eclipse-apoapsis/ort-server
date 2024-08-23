/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.core.commands

import org.eclipse.apoapsis.ortserver.core.commands.createorganization.CreateOrganizationCommand
import org.eclipse.apoapsis.ortserver.model.Organization
import org.eclipse.apoapsis.ortserver.services.OrganizationService

/**
 * A [CommandHandler] to handle a [CreateOrganizationCommand].
 */
class CreateOrganizationCommandHandler(private val organizationService: OrganizationService) :
    CommandHandler<CreateOrganizationCommand, Organization> {
    override suspend fun execute(command: CreateOrganizationCommand) =
        runCatching {
            organizationService.createOrganization(command.name, command.description)
        }
}
