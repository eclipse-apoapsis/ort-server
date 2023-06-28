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

package org.ossreviewtoolkit.server.workers.common.env.config

import org.ossreviewtoolkit.server.model.InfrastructureService
import org.ossreviewtoolkit.server.workers.common.env.definition.EnvironmentServiceDefinition
import org.ossreviewtoolkit.server.workers.common.env.definition.MavenDefinition
import org.ossreviewtoolkit.server.workers.common.env.definition.NpmAuthMode
import org.ossreviewtoolkit.server.workers.common.env.definition.NpmDefinition

/**
 * A helper class for creating concrete [EnvironmentServiceDefinition] instances from the properties declared in an
 * environment configuration file.
 */
class EnvironmentDefinitionFactory {
    companion object {
        /** The name for the [MavenDefinition] type. */
        const val MAVEN_TYPE = "maven"

        /** The name for the [NpmDefinition] type. */
        const val NPM_TYPE = "npm"

        /** The name of the property that defines the infrastructure service for a definition. */
        const val SERVICE_PROPERTY = "service"
    }

    /**
     * Return a [Result] with the [EnvironmentServiceDefinition] created for the given [type], [service], and
     * [properties]. If the creation fails due to invalid properties, return a failure result.
     */
    fun createDefinition(
        type: String,
        service: InfrastructureService,
        properties: Map<String, String>
    ): Result<EnvironmentServiceDefinition> =
        when (type) {
            MAVEN_TYPE -> createMavenDefinition(service, DefinitionProperties(properties))
            NPM_TYPE -> createNpmDefinition(service, DefinitionProperties(properties))
            else -> fail("Unsupported definition type '$type'", properties)
        }

    /**
     * Create a definition for Maven's _settings.xml_ file with the given [service] and [properties].
     */
    private fun createMavenDefinition(
        service: InfrastructureService,
        properties: DefinitionProperties
    ): Result<EnvironmentServiceDefinition> =
        properties.withRequiredProperties("id") { MavenDefinition(service, getProperty("id")) }

    /**
     * Create a definition for the _.npmrc_ configuration file of NPM with the given [service] and [properties].
     */
    private fun createNpmDefinition(
        service: InfrastructureService,
        properties: DefinitionProperties
    ): Result<EnvironmentServiceDefinition> =
        properties.withRequiredProperties {
            NpmDefinition(
                service = service,
                scope = getOptionalProperty("scope"),
                email = getOptionalProperty("email"),
                authMode = getEnumProperty("authMode", NpmAuthMode.PASSWORD),
                alwaysAuth = getBooleanProperty("alwaysAuth", true)
            )
        }
}

/**
 * A helper class for accessing the properties of an environment definition. The class offers functionality to check
 * whether all required properties have been specified or if unsupported properties are contained.
 */
private class DefinitionProperties(val properties: Map<String, String>) {
    /** A set to check whether unsupported properties are present. */
    private val consumedProperties = (properties.keys - EnvironmentDefinitionFactory.SERVICE_PROPERTY).toMutableSet()

    /**
     * Check whether all the given [names] are contained in this object and execute the given [block].
     */
    fun <T> withRequiredProperties(vararg names: String, block: DefinitionProperties.() -> T): Result<T> {
        val namesList = names.toSet()
        consumedProperties -= namesList

        return if (properties.keys.containsAll(namesList)) {
            runCatching(block).takeIf { consumedProperties.isEmpty() }
                ?: fail("Unsupported properties found: ${consumedProperties.joinToString { "'$it'" }}", properties)
        } else {
            fail("Missing required properties: ${namesList.joinToString { "'$it'" }}", properties)
        }
    }

    /**
     * Return the value of the required property with the given [name].
     */
    fun getProperty(name: String): String = properties.getValue(name)

    /**
     * Return the value of the property with the given [name] or *null* if it is not defined.
     */
    fun getOptionalProperty(name: String): String? =
        properties[name].also { consumedProperties -= name }

    /**
     * Return the value of a property of an Enum type with the given [name] or the given [default] if this property
     * is not defined. Throw an [EnvironmentConfigException] if the value is invalid.
     */
    inline fun <reified T : Enum<T>> getEnumProperty(name: String, default: T): T {
        return getOptionalProperty(name)?.let { value ->
            val allowedValues = enumValues<T>()
            allowedValues.find { it.name.equals(value, ignoreCase = true) }
                ?: throw EnvironmentConfigException(
                    "Invalid valid for property '$name': '$value'. " +
                            "Allowed values are: ${allowedValues.joinToString()} with properties $properties."
                )
        } ?: default
    }

    /**
     * Return the value of the boolean property with the given [name] or the given [default] if this property is not
     * defined. Throw an [EnvironmentConfigException] if the value is not a valid boolean literal.
     */
    fun getBooleanProperty(name: String, default: Boolean): Boolean =
        getEnumProperty<BooleanProperty>(name, booleanPropertyMap.getValue(default)) == BooleanProperty.TRUE
}

/**
 * Throw an exception with the given [message] that is extended by the given [properties].
 */
private fun <T> fail(message: String, properties: Map<String, String>): Result<T> =
    Result.failure(EnvironmentConfigException("$message with properties $properties."))

/**
 * An internally used enum class to represent boolean property values and validate them.
 */
private enum class BooleanProperty {
    TRUE,
    FALSE
}

/**
 * A mapping from boolean values to their corresponding constants in [BooleanProperty].
 */
private val booleanPropertyMap = mapOf(true to BooleanProperty.TRUE, false to BooleanProperty.FALSE)
