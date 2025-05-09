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

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.andThen
import com.github.michaelbull.result.map
import com.github.michaelbull.result.toErrorIfNull
import com.github.michaelbull.result.toResultOr

import org.eclipse.apoapsis.ortserver.components.pluginmanager.queries.GetPluginTemplateQuery
import org.eclipse.apoapsis.ortserver.components.pluginmanager.queries.GetPluginTemplatesQuery

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.model.utils.DatabaseUtils.transaction

class PluginTemplateService(
    private val db: Database,
    private val eventStore: PluginTemplateEventStore
) {
    internal fun addOrganization(
        templateName: String,
        pluginType: PluginType,
        pluginId: String,
        organizationId: Long,
        userId: String
    ): Result<Unit, TemplateError> =
        db.transaction {
            validatePlugin(pluginType, pluginId)
                .andThen { normalizedPluginId -> getTemplateState(templateName, pluginType, normalizedPluginId) }
                .andThen(::validateNotDeleted)
                .andThen { template ->
                    template.takeUnless { organizationId in template.organizationIds }.toResultOr {
                        TemplateError.InvalidState(
                            "The organization with ID '$organizationId' is already added to the plugin template " +
                                    "'$templateName' for plugin type '$pluginType' and ID '$pluginId'."
                        )
                    }
                }
                .map { template ->
                    eventStore.appendEvent(
                        PluginTemplateEvent(
                            name = template.name,
                            pluginType = template.pluginType,
                            pluginId = template.pluginId,
                            version = template.version + 1,
                            payload = OrganizationAdded(organizationId),
                            createdBy = userId
                        )
                    )
                }
        }

    internal fun delete(
        templateName: String,
        pluginType: PluginType,
        pluginId: String,
        userId: String
    ): Result<Unit, TemplateError> = db.transaction {
        validatePlugin(pluginType, pluginId)
            .andThen { normalizedPluginId -> getTemplateState(templateName, pluginType, normalizedPluginId) }
            .andThen(::validateNotDeleted)
            .map { template ->
                eventStore.appendEvent(
                    PluginTemplateEvent(
                        name = template.name,
                        pluginType = template.pluginType,
                        pluginId = template.pluginId,
                        version = template.version + 1,
                        payload = Deleted,
                        createdBy = userId
                    )
                )
            }
    }

    internal fun disableGlobal(
        templateName: String,
        pluginType: PluginType,
        pluginId: String,
        userId: String
    ): Result<Unit, TemplateError> = db.transaction {
        validatePlugin(pluginType, pluginId)
            .andThen { normalizedPluginId -> getTemplateState(templateName, pluginType, normalizedPluginId) }
            .andThen(::validateNotDeleted)
            .andThen { template ->
                template.takeIf { it.isGlobal }.toResultOr {
                    TemplateError.InvalidState(
                        "The plugin template '$templateName' for plugin type '$pluginType' and ID '$pluginId' is " +
                                "not enabled globally."
                    )
                }
            }.map { template ->
                eventStore.appendEvent(
                    PluginTemplateEvent(
                        name = template.name,
                        pluginType = template.pluginType,
                        pluginId = template.pluginId,
                        version = template.version + 1,
                        payload = GlobalDisabled,
                        createdBy = userId
                    )
                )
            }
    }

    internal fun enableGlobal(
        templateName: String,
        pluginType: PluginType,
        pluginId: String,
        userId: String
    ): Result<Unit, TemplateError> = db.transaction {
        validatePlugin(pluginType, pluginId)
            .andThen { normalizedPluginId -> getTemplateState(templateName, pluginType, normalizedPluginId) }
            .andThen(::validateNotDeleted)
            .andThen { template ->
                template.takeIf { !it.isGlobal }.toResultOr {
                    TemplateError.InvalidState(
                        "The plugin template '$templateName' for plugin type '$pluginType' and ID '$pluginId' is " +
                                "already enabled globally."
                    )
                }
            }.map { template ->
                eventStore.appendEvent(
                    PluginTemplateEvent(
                        name = template.name,
                        pluginType = template.pluginType,
                        pluginId = template.pluginId,
                        version = template.version + 1,
                        payload = GlobalEnabled,
                        createdBy = userId
                    )
                )
            }
    }

    internal fun getTemplate(
        templateName: String, pluginType: PluginType, pluginId: String
    ): Result<PluginTemplate, TemplateError> = db.transaction {
        validatePlugin(pluginType, pluginId)
            .map { normalizedPluginId ->
                GetPluginTemplateQuery(templateName, pluginType, normalizedPluginId).execute()
            }.toErrorIfNull {
                TemplateError.NotFound(
                    "No plugin template with name '$templateName' for plugin type '$pluginType' and ID '$pluginId' " +
                            "found."
                )
            }
    }

    internal fun getTemplates(
        pluginType: PluginType, pluginId: String
    ): Result<List<PluginTemplate>, TemplateError> = db.transaction {
        validatePlugin(pluginType, pluginId)
            .map { normalizedPluginId -> GetPluginTemplatesQuery(pluginType, normalizedPluginId).execute() }
    }

    internal fun removeOrganization(
        templateName: String,
        pluginType: PluginType,
        pluginId: String,
        organizationId: Long,
        userId: String
    ): Result<Unit, TemplateError> =
        db.transaction {
            validatePlugin(pluginType, pluginId)
                .andThen { normalizedPluginId -> getTemplateState(templateName, pluginType, normalizedPluginId) }
                .andThen(::validateNotDeleted)
                .andThen { template ->
                    template.takeIf { organizationId in template.organizationIds }.toResultOr {
                        TemplateError.InvalidState(
                            "The organization with ID '$organizationId' is not added to the plugin template " +
                                    "'$templateName' for plugin type '$pluginType' and ID '$pluginId'."
                        )
                    }
                }
                .map { template ->
                    eventStore.appendEvent(
                        PluginTemplateEvent(
                            name = template.name,
                            pluginType = template.pluginType,
                            pluginId = template.pluginId,
                            version = template.version + 1,
                            payload = OrganizationRemoved(organizationId),
                            createdBy = userId
                        )
                    )
                }
        }

    internal fun updateOptions(
        templateName: String,
        pluginType: PluginType,
        pluginId: String,
        userId: String,
        options: List<PluginOptionTemplate>
    ): Result<Unit, TemplateError> = db.transaction {
        validatePlugin(pluginType, pluginId)
            .andThen { normalizedPluginId -> getTemplateStateOrEmpty(templateName, pluginType, normalizedPluginId) }
            .map { template ->
                eventStore.appendEvent(
                    PluginTemplateEvent(
                        name = template.name,
                        pluginType = template.pluginType,
                        pluginId = template.pluginId,
                        version = template.version + 1,
                        payload = OptionsUpdated(options),
                        createdBy = userId
                    )
                )
            }
    }

    /** Returns the [PluginTemplateState] with the given [templateName], [pluginType], and [pluginId] if it exists. */
    private fun getTemplateState(
        templateName: String,
        pluginType: PluginType,
        pluginId: String
    ): Result<PluginTemplateState, TemplateError> {
        val template = eventStore.getPluginTemplate(templateName, pluginType, pluginId)

        return if (template == null) {
            TemplateError.NotFound(
                "No plugin template with name '$templateName' for plugin type '$pluginType' and ID '$pluginId' found."
            ).toErr()
        } else {
            Ok(template)
        }
    }

    /**
     * Returns the [PluginTemplateState] with the given [templateName], [pluginType], and [pluginId] if it exists, or
     * an empty template state if it does not exist.
     */
    private fun getTemplateStateOrEmpty(
        templateName: String,
        pluginType: PluginType,
        pluginId: String
    ): Result<PluginTemplateState, TemplateError> =
        Ok(
            eventStore.getPluginTemplate(templateName, pluginType, pluginId)
                ?: PluginTemplateState(templateName, pluginType, pluginId)
        )

    /** Validate that the plugin template is not deleted. */
    private fun validateNotDeleted(template: PluginTemplateState): Result<PluginTemplateState, TemplateError> =
        if (template.isDeleted) {
            TemplateError.InvalidState(
                "The plugin template '${template.name}' for plugin type '${template.pluginType}' and ID " +
                        "'${template.pluginId}' is deleted."
            ).toErr()
        } else {
            Ok(template)
        }

    /**
     * Validate that the plugin with the given [pluginType] and [pluginId] is installed and return its normalized ID on
     * success.
     */
    private fun validatePlugin(pluginType: PluginType, pluginId: String): Result<String, TemplateError> =
        normalizePluginId(pluginType, pluginId)?.let { Ok(it) }
            ?: TemplateError.InvalidPlugin("No plugin with type '$pluginType' and ID '$pluginId' found.").toErr()
}

internal sealed interface TemplateError {
    data class InvalidPlugin(val message: String) : TemplateError
    data class InvalidState(val message: String) : TemplateError
    data class NotFound(val message: String) : TemplateError
}
