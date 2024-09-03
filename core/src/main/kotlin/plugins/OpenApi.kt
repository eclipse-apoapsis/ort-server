/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 * Copyright 2024 SMILEY4
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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.AuthType
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.github.smiley4.schemakenerator.core.connectSubTypes
import io.github.smiley4.schemakenerator.core.data.BaseTypeData
import io.github.smiley4.schemakenerator.core.data.Bundle
import io.github.smiley4.schemakenerator.core.data.TypeId
import io.github.smiley4.schemakenerator.core.data.flatten
import io.github.smiley4.schemakenerator.core.handleNameAnnotation
import io.github.smiley4.schemakenerator.reflection.collectSubTypes
import io.github.smiley4.schemakenerator.reflection.processReflection
import io.github.smiley4.schemakenerator.swagger.OptionalHandling
import io.github.smiley4.schemakenerator.swagger.data.CompiledSwaggerSchema
import io.github.smiley4.schemakenerator.swagger.data.SwaggerSchema
import io.github.smiley4.schemakenerator.swagger.generateSwaggerSchema
import io.github.smiley4.schemakenerator.swagger.handleCoreAnnotations
import io.github.smiley4.schemakenerator.swagger.steps.SwaggerSchemaCompileUtils.merge
import io.github.smiley4.schemakenerator.swagger.steps.SwaggerSchemaCompileUtils.resolveReferences
import io.github.smiley4.schemakenerator.swagger.steps.SwaggerSchemaCompileUtils.shouldReference
import io.github.smiley4.schemakenerator.swagger.steps.SwaggerSchemaUtils
import io.github.smiley4.schemakenerator.swagger.steps.buildTypeDataMap

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

import io.swagger.v3.oas.models.media.Schema

import kotlinx.datetime.Instant

import org.eclipse.apoapsis.ortserver.api.v1.model.CredentialsType
import org.eclipse.apoapsis.ortserver.api.v1.model.OptionalValue
import org.eclipse.apoapsis.ortserver.api.v1.model.RepositoryType
import org.eclipse.apoapsis.ortserver.model.ORT_SERVER_VERSION

import org.koin.ktor.ext.inject

fun Application.configureOpenApi() {
    val config: ApplicationConfig by inject()

    install(SwaggerUI) {
        // Don't show the routes providing the custom json-schemas.
        pathFilter = { _, url -> url.firstOrNull() != "schemas" }

        security {
            defaultSecuritySchemeNames = listOf(SecurityConfigurations.token)
            defaultUnauthorizedResponse {
                description = "Invalid Token"
            }

            securityScheme(SecurityConfigurations.token) {
                type = AuthType.OAUTH2
                flows {
                    authorizationCode {
                        authorizationUrl = "${config.property("jwt.issuer").getString()}/protocol/openid-connect/auth"
                        tokenUrl = "${config.property("jwt.issuer").getString()}/protocol/openid-connect/token"
                        scopes = emptyMap()
                    }
                }
            }
        }

        info {
            title = "ORT Server API"
            version = ORT_SERVER_VERSION
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }

        server {
            val scheme = config.property("ktor.deployment.publicScheme").getString()
            val fqdn = config.property("ktor.deployment.publicFqdn").getString()
            val port = config.property("ktor.deployment.publicPort").getString()
            url = "$scheme://$fqdn:$port"
            description = "ORT server"
        }

        // OpenAPI provides tags not only on operation level, but also on root level.
        // This allows to provide additional information to the tags, and actually the order
        // of the tags on root level also defines the order of appearance of the operations
        // (belonging to these tags) in the Swagger UI.
        // See https://swagger.io/docs/specification/grouping-operations-with-tags/ for details.
        tags {
            tag("Health") { }
            tag("Organizations") { }
            tag("Products") { }
            tag("Repositories") { }
            tag("Runs") { }
            tag("Secrets") { }
            tag("Infrastructure services") { }
            tag("Reports") { }
            tag("Logs") { }
        }

        schemas {
            generator = { type ->
                type
                    .collectSubTypes()
                    .processReflection {
                        // Replace Instants with Strings in the generated schema to avoid breaking changes in the UI.
                        // This might later be replaced with a proper schema for dates.
                        redirect<Instant, String>()
                        redirect<Instant?, String?>()

                        // Replace OptionalValue with its type argument in the generated schema as the class is only
                        // required in Kotlin code to model the difference between not present and null. Data classes
                        // using OptionalValue must provide a default value to properly mark the element as not required
                        // in the generated schema.
                        redirect<OptionalValue<String>, String>()
                        redirect<OptionalValue<String?>, String?>()
                        redirect<OptionalValue<RepositoryType>, RepositoryType>()
                        redirect<OptionalValue<Set<CredentialsType>>, Set<CredentialsType>>()
                    }
                    .connectSubTypes()
                    .handleNameAnnotation()
                    .generateSwaggerSchema {
                        optionalHandling = OptionalHandling.NON_REQUIRED
                    }
                    .handleCoreAnnotations()
                    .withCustomTitle()
                    .customCompileReferencingRoot()
            }
        }
    }

    routing {
        route("swagger-ui") {
            swaggerUI("/swagger-ui/api.json")

            route("api.json") {
                openApiSpec()
            }
        }
    }
}

private fun Bundle<SwaggerSchema>.withCustomTitle(): Bundle<SwaggerSchema> {
    return CustomTitleStep().process(this)
}

/**
 * An adapted version of [io.github.smiley4.schemakenerator.swagger.steps.SwaggerSchemaAutoTitleStep] that generates
 * valid titles for the generated schemas. For example, the type `GenericType<Type1, Type2>` is transformed to
 * `GenericType_Type1-Type2` instead of `GenericType<Type1,Type2>`.
 *
 * According to the [specification](https://swagger.io/specification/#components-object), schema keys must match the
 * regular expression `^[a-zA-Z0-9\.\-_]+$`.
 */
private class CustomTitleStep {
    fun process(bundle: Bundle<SwaggerSchema>): Bundle<SwaggerSchema> {
        val typeDataMap = bundle.buildTypeDataMap()
        return bundle.also { schema ->
            process(schema.data, typeDataMap)
            schema.supporting.forEach { process(it, typeDataMap) }
        }
    }

    private fun process(schema: SwaggerSchema, typeDataMap: Map<TypeId, BaseTypeData>) {
        if (schema.swagger.title == null) {
            schema.swagger.title = determineTitle(schema.typeData, typeDataMap)
        }
    }

    private fun determineTitle(typeData: BaseTypeData, typeDataMap: Map<TypeId, BaseTypeData>): String {
        return typeData.simpleName.let {
            if (typeData.typeParameters.isNotEmpty()) {
                val paramString = typeData.typeParameters
                    .map { (_, param) -> determineTitle(typeDataMap.getValue(param.type), typeDataMap) }
                    .joinToString("-")
                "${it}_$paramString"
            } else {
                it
            }
        }.let {
            it + (typeData.id.additionalId?.let { a -> "#$a" }.orEmpty())
        }
    }
}

private fun Bundle<SwaggerSchema>.customCompileReferencingRoot(): CompiledSwaggerSchema {
    return CustomCompileReferenceRootStep().compile(this)
}

/**
 * An adapted version of [io.github.smiley4.schemakenerator.swagger.steps.SwaggerSchemaCompileReferenceRootStep] that
 * generates valid references for the generated schemas (see [getRefPath]).
 */
private class CustomCompileReferenceRootStep {
    private val schemaUtils = SwaggerSchemaUtils()

    /**
     * Put referenced schemas into definitions and reference them
     */
    fun compile(bundle: Bundle<SwaggerSchema>): CompiledSwaggerSchema {
        val result = CustomCompileReferenceStep().compile(bundle)
        if (shouldReference(result.swagger)) {
            val refPath = getRefPath(result.typeData, bundle.buildTypeDataMap())
            return CompiledSwaggerSchema(
                typeData = result.typeData,
                swagger = schemaUtils.referenceSchema(refPath, true),
                componentSchemas = buildMap {
                    this.putAll(result.componentSchemas)
                    this[refPath] = result.swagger
                }
            )
        } else {
            return CompiledSwaggerSchema(
                typeData = result.typeData,
                swagger = result.swagger,
                componentSchemas = result.componentSchemas
            )
        }
    }
}

/**
 * An adapted version of [io.github.smiley4.schemakenerator.swagger.steps.SwaggerSchemaCompileReferenceStep] that
 * generates valid references for the generated schemas (see [getRefPath]).
 */
private class CustomCompileReferenceStep {
    private val schemaUtils = SwaggerSchemaUtils()

    /**
     * Put referenced schemas into definitions and reference them
     */
    fun compile(bundle: Bundle<SwaggerSchema>): CompiledSwaggerSchema {
        val schemaList = bundle.flatten()
        val typeDataMap = bundle.buildTypeDataMap()
        val components = mutableMapOf<String, Schema<*>>()

        val root = resolveReferences(bundle.data.swagger) { refObj ->
            resolve(refObj, schemaList, typeDataMap, components)
        }

        return CompiledSwaggerSchema(
            typeData = bundle.data.typeData,
            swagger = root,
            componentSchemas = components
        )
    }

    private fun resolve(
        refObj: Schema<*>,
        schemaList: List<SwaggerSchema>,
        typeDataMap: Map<TypeId, BaseTypeData>,
        components: MutableMap<String, Schema<*>>
    ): Schema<*> {
        val referencedId = TypeId.parse(refObj.`$ref`)
        val referencedSchema = schemaList.find(referencedId)
        return if (referencedSchema != null) {
            if (shouldReference(referencedSchema.swagger)) {
                val refPath = getRefPath(referencedSchema.typeData, typeDataMap)
                if (!components.containsKey(refPath)) {
                    components[refPath] = placeholder() // break out of infinite loops
                    components[refPath] =
                        resolveReferences(referencedSchema.swagger) { resolve(it, schemaList, typeDataMap, components) }
                }
                schemaUtils.referenceSchema(refPath, true)
            } else {
                merge(refObj, referencedSchema.swagger)
            }
        } else {
            refObj
        }
    }

    private fun placeholder() = Schema<Any>()

    private fun Collection<SwaggerSchema>.find(id: TypeId): SwaggerSchema? {
        return this.find { it.typeData.id == id }
    }
}

/**
 * An adapted version of [io.github.smiley4.schemakenerator.swagger.steps.SwaggerSchemaCompileUtils.getRefPath] that
 * generates valid references for the generated schemas. For example, the type `GenericType<Type1, Type2>` is
 * transformed to `GenericType_Type1-Type2` instead of `GenericType<Type1,Type2>`.
 *
 * According to the [specification](https://swagger.io/specification/#components-object), schema keys must match the
 * regular expression `^[a-zA-Z0-9\.\-_]+$`.
 */
private fun getRefPath(typeData: BaseTypeData, typeDataMap: Map<TypeId, BaseTypeData>): String {
    return typeData.simpleName.let {
        if (typeData.typeParameters.isNotEmpty()) {
            val paramString = typeData.typeParameters
                .map { (_, param) -> getRefPath(typeDataMap.getValue(param.type), typeDataMap) }
                .joinToString("-")
            "${it}_$paramString"
        } else {
            it
        }
    }.let {
        it + (typeData.id.additionalId?.let { a -> "#$a" }.orEmpty())
    }
}
