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
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.toErrorIfNull
import com.github.michaelbull.result.toResultOr

import org.eclipse.apoapsis.ortserver.components.pluginmanager.queries.GetPluginTemplateForOrganizationQuery
import org.eclipse.apoapsis.ortserver.components.pluginmanager.queries.GetPluginTemplateQuery
import org.eclipse.apoapsis.ortserver.components.pluginmanager.queries.GetPluginTemplatesQuery
import org.eclipse.apoapsis.ortserver.model.PluginConfig
import org.eclipse.apoapsis.ortserver.model.repositories.OrganizationRepository
import org.eclipse.apoapsis.ortserver.model.repositories.RepositoryRepository

import org.jetbrains.exposed.sql.Database

import org.ossreviewtoolkit.model.utils.DatabaseUtils.transaction
import org.ossreviewtoolkit.utils.ort.runBlocking

/** A service for managing plugin templates. */
@Suppress("TooManyFunctions")
class PluginTemplateService(
    private val db: Database,
    private val eventStore: PluginTemplateEventStore,
    private val pluginService: PluginService,
    private val organizationRepository: OrganizationRepository,
    private val repositoryRepository: RepositoryRepository
) {
    /**
     * Assign the plugin template with the given [templateName], [pluginType], and [pluginId] to the organization with
     * the given [organizationId].
     */
    internal fun addOrganization(
        templateName: String,
        pluginType: PluginType,
        pluginId: String,
        organizationId: Long,
        userId: String
    ): Result<Unit, TemplateError> =
        db.transaction {
            validateOrganizationExists(organizationId)
                .andThen { validatePlugin(pluginType, pluginId) }
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

    /**
     * Create a new plugin template with the given [templateName], [pluginType], and [pluginId] with the provided
     * [options].
     */
    internal fun create(
        templateName: String,
        pluginType: PluginType,
        pluginId: String,
        userId: String,
        options: List<PluginOptionTemplate>
    ): Result<Unit, TemplateError> = db.transaction {
        validatePlugin(pluginType, pluginId)
            .andThen { normalizedPluginId -> validatePluginOptions(pluginType, normalizedPluginId, options) }
            .andThen { normalizedPluginId -> validateNotExisting(templateName, pluginType, normalizedPluginId) }
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

    /** Delete the plugin template with the given [templateName], [pluginType], and [pluginId]. */
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

    /** Disable the plugin template with the given [templateName], [pluginType], and [pluginId] globally. */
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

    /** Enable the plugin template with the given [templateName], [pluginType], and [pluginId] globally. */
    internal fun enableGlobal(
        templateName: String,
        pluginType: PluginType,
        pluginId: String,
        userId: String
    ): Result<Unit, TemplateError> = db.transaction {
        validatePlugin(pluginType, pluginId)
            .andThen { normalizedPluginId -> getTemplateState(templateName, pluginType, normalizedPluginId) }
            .andThen(::validateNotDeleted)
            .andThen { template -> validateNoOtherGlobalTemplate(template) }
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

    internal fun getPluginsForRepository(
        repositoryId: Long
    ): Result<List<PreconfiguredPluginDescriptor>, TemplateError> = db.transaction {
        val organizationId = repositoryRepository.get(repositoryId)?.organizationId

        if (organizationId == null) {
            return@transaction TemplateError.NotFound("No repository with ID '$repositoryId' found.").toErr()
        }

        pluginService.getPlugins().filter { it.enabled }
            .fold(initial = emptyList<PreconfiguredPluginDescriptor>()) { acc, plugin ->
                getTemplateForOrganization(plugin.type, plugin.id, organizationId).map { template ->
                    acc + PreconfiguredPluginDescriptor(
                        id = plugin.id,
                        type = plugin.type,
                        displayName = plugin.displayName,
                        description = plugin.description,
                        options = plugin.options.map { option ->
                            val templateOption = template?.options?.find { it.option == option.name }

                            PreconfiguredPluginOption(
                                name = option.name,
                                description = option.description,
                                type = option.type,
                                defaultValue = templateOption?.value ?: option.defaultValue,
                                isFixed = templateOption?.isFinal == true,
                                isNullable = option.isNullable,
                                isRequired = option.isRequired
                            )
                        }
                    )
                }
            }
    }

    /** Return the plugin template with the given [templateName], [pluginType], and [pluginId]. */
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

    /**
     *  Return the plugin template with the given [pluginType], [pluginId] for the organization with the given
     *  [organizationId]. This is either a template that is assigned to the organization, a global template if no
     *  template is assigned to the organization, or `null` if no template exists for the organization.
     */
    internal fun getTemplateForOrganization(
        pluginType: PluginType,
        pluginId: String,
        organizationId: Long
    ): Result<PluginTemplate?, TemplateError> = db.transaction {
        validatePlugin(pluginType, pluginId)
            .map { normalizedPluginId ->
                GetPluginTemplateForOrganizationQuery(pluginType, normalizedPluginId, organizationId).execute()
            }
    }

    /** Return the plugin templates for the given [pluginType] and [pluginId]. */
    internal fun getTemplates(
        pluginType: PluginType, pluginId: String
    ): Result<List<PluginTemplate>, TemplateError> = db.transaction {
        validatePlugin(pluginType, pluginId)
            .map { normalizedPluginId -> GetPluginTemplatesQuery(pluginType, normalizedPluginId).execute() }
    }

    /**
     * Remove the plugin template with the given [templateName], [pluginType], and [pluginId] from the organization
     * with the given [organizationId].
     */
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

    /**
     * Update the plugin template with the given [templateName], [pluginType], and [pluginId] with the provided
     * [options].
     */
    internal fun updateOptions(
        templateName: String,
        pluginType: PluginType,
        pluginId: String,
        userId: String,
        options: List<PluginOptionTemplate>
    ): Result<Unit, TemplateError> = db.transaction {
        validatePlugin(pluginType, pluginId)
            .andThen { normalizedPluginId -> validatePluginOptions(pluginType, normalizedPluginId, options) }
            .andThen { normalizedPluginId -> getTemplateState(templateName, pluginType, normalizedPluginId) }
            .andThen(::validateNotDeleted)
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

    /**
     * Validate the provided [pluginConfigs] against the admin configuration for the provided
     * [organization][organizationId].
     */
    fun validatePluginConfigs(
        pluginConfigs: Map<PluginType, Map<String, PluginConfig>>,
        organizationId: Long
    ): PluginConfigValidationResult {
        validateOrganizationExists(organizationId).onFailure { error ->
            return PluginConfigValidationResult(listOf(error.message))
        }

        val plugins = pluginService.getPlugins()
        val errors = mutableListOf<String>()

        pluginConfigs.forEach { (pluginType, configs) ->
            configs.forEach { (pluginId, config) ->
                val descriptor = plugins.singleOrNull {
                    it.type == pluginType && it.id == normalizePluginId(pluginType, pluginId)
                }

                if (descriptor == null) {
                    errors += "The plugin with type '$pluginType' and ID '$pluginId' is not installed."
                    return@forEach
                }

                if (!pluginService.isEnabled(pluginType, pluginId)) {
                    errors += "The plugin with type '$pluginType' and ID '$pluginId' is disabled by the administrators."
                    return@forEach
                }

                val template = getTemplateForOrganization(pluginType, pluginId, organizationId).getOrElse {
                    errors += it.message
                    return@forEach
                }

                if (template == null) {
                    return@forEach
                }

                // Check if option is set which is fixed by a template.
                (config.options.keys + config.secrets.keys).forEach { option ->
                    val pluginOption = descriptor.options.find { it.name == option }

                    if (pluginOption == null) {
                        errors += "The plugin option '$option' is not defined for the plugin with type " +
                                "'$pluginType' and ID '$pluginId'."
                    }

                    val templateOption = template.options.find { it.option == option }

                    if (templateOption?.isFinal == true) {
                        errors += "The plugin option '$option' for the plugin with type '$pluginType' and ID " +
                                "'$pluginId' is set to a fixed value by the server administrators and cannot be" +
                                " changed."
                    }
                }
            }
        }

        return PluginConfigValidationResult(errors)
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
     * Validate that no plugin template with the given [templateName], [pluginType], and [pluginId] already exists and
     * return the [pluginId] on success.
     */
    private fun validateNotExisting(
        templateName: String,
        pluginType: PluginType,
        pluginId: String
    ): Result<String, TemplateError> {
        val template = eventStore.getPluginTemplate(templateName, pluginType, pluginId)

        return if (template != null && !template.isDeleted) {
            return TemplateError.InvalidState(
                "A plugin template with name '$templateName' for plugin type '$pluginType' and ID '$pluginId' " +
                        "already exists."
            ).toErr()
        } else {
            Ok(pluginId)
        }
    }

    /** Validate that the organization with the given [organizationId] exists. */
    private fun validateOrganizationExists(organizationId: Long): Result<Unit, TemplateError> =
        runBlocking {
            if (organizationRepository.get(organizationId) == null) {
                TemplateError.NotFound("No organization with ID '$organizationId' found.").toErr()
            } else {
                Ok(Unit)
            }
        }

    /** Validate that there is no other global template for the same plugin. */
    private fun validateNoOtherGlobalTemplate(
        template: PluginTemplateState
    ): Result<PluginTemplateState, TemplateError> =
        getTemplates(template.pluginType, template.pluginId).map { templates ->
            val globalTemplate = templates.find { it.name != template.name && it.isGlobal }
            if (globalTemplate != null) {
                return TemplateError.InvalidState(
                    "The plugin template '${template.name}' for plugin type '${template.pluginType}' and ID " +
                            "'${template.pluginId}' cannot be enabled globally because there is already a global " +
                            "template with name '${globalTemplate.name}'."
                ).toErr()
            } else {
                template
            }
        }

    /**
     * Validate that the plugin with the given [pluginType] and [pluginId] is installed and return its normalized ID on
     * success.
     */
    private fun validatePlugin(pluginType: PluginType, pluginId: String): Result<String, TemplateError> =
        normalizePluginId(pluginType, pluginId)?.let { Ok(it) }
            ?: TemplateError.InvalidPlugin("No plugin with type '$pluginType' and ID '$pluginId' found.").toErr()

    /**
     * Validate that the provided [options] are valid for the given [pluginType] and [pluginId]. Returns the plugin ID
     * on success.
     */
    private fun validatePluginOptions(
        pluginType: PluginType,
        pluginId: String,
        options: List<PluginOptionTemplate>
    ): Result<String, TemplateError> {
        val descriptor = pluginService.getPlugins().single { it.type == pluginType && it.id == pluginId }

        options.forEach { option ->
            val pluginOption = descriptor.options.find { it.name == option.option }

            if (pluginOption == null) {
                return TemplateError.InvalidPlugin(
                    "The plugin option '${option.option}' is not defined for the plugin with type " +
                            "'$pluginType' and ID '$pluginId'."
                ).toErr()
            }

            if (pluginOption.type != option.type) {
                return TemplateError.InvalidPlugin(
                    "The plugin option '${option.option}' has type '${option.type}' but expected type is " +
                            "'${pluginOption.type}' for the plugin with type '$pluginType' and ID '$pluginId'."
                ).toErr()
            }

            // Perform a basic validation of the option value based on its type.
            val canBeParsed = (pluginOption.isNullable && option.value == null) ||
                    when (pluginOption.type) {
                        PluginOptionType.BOOLEAN -> option.value?.toBooleanStrictOrNull() != null
                        PluginOptionType.INTEGER -> option.value?.toIntOrNull() != null
                        PluginOptionType.LONG -> option.value?.toLongOrNull() != null
                        PluginOptionType.SECRET -> option.value?.isNotBlank() == true
                        PluginOptionType.STRING -> true
                        PluginOptionType.STRING_LIST -> true
                    }

            if (!canBeParsed) {
                return TemplateError.InvalidPlugin(
                    "The plugin option '${option.option}' has value '${option.value}' which cannot be parsed " +
                            "as type '${pluginOption.type}' for the plugin with type '$pluginType' and ID '$pluginId'."
                ).toErr()
            }
        }

        return Ok(pluginId)
    }
}

internal sealed interface TemplateError {
    val message: String

    data class InvalidPlugin(override val message: String) : TemplateError
    data class InvalidState(override val message: String) : TemplateError
    data class NotFound(override val message: String) : TemplateError
}

/** The result of validating plugin configs against the admin configuration for a specific organization. */
class PluginConfigValidationResult(
    /** The list of errors found during validation. */
    val errors: List<String>
) {
    /** Whether the plugin configuration is valid. */
    val isValid = errors.isEmpty()
}
