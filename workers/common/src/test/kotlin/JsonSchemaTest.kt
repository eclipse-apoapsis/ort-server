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

package org.eclipse.apoapsis.ortserver.workers.common

import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import com.networknt.schema.serialization.NodeReader

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

import java.io.File

import org.ossreviewtoolkit.model.yamlMapper

class JsonSchemaTest : WordSpec({
    val validConfigs = File("src/test/resources")
        .walk().maxDepth(1)
        .filter { it.isFile && it.name.matches(Regex("\\.ort\\.env(\\..*)?\\.yml")) }

    val invalidConfigs = File("src/test/resources/invalid")
        .walk().maxDepth(1)
        .filter { it.isFile && it.name.matches(Regex("\\.ort\\.env(\\..*)?\\.yml")) }

    "JSON schema" should {
        "successfully validate all valid .ort.env.yml files" {
            @Suppress("IgnoredReturnValue")
            validConfigs.forAll { file ->
                val errors = schema.validate(file.toJsonNode())

                errors should beEmpty()
            }
        }

        "fail to validate all invalid .ort.env.yml files" {
            // While non-strict environment configurations are strictly speaking never invalid, they will still be
            // treated as invalid by the JSON schema validator.
            @Suppress("IgnoredReturnValue")
            invalidConfigs.forAll { file ->
                val errors = schema.validate(file.toJsonNode())

                errors shouldNot beEmpty()
            }
        }
    }
})

private fun File.toJsonNode() = yamlMapper.readTree(inputStream())

private val nodeReader = NodeReader.builder().yamlMapper(yamlMapper).build()

private val schemaV7 = SchemaRegistry
    .builder(SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7))
    .nodeReader(nodeReader)
    .build()

private val schema = schemaV7.getSchema(
    File("../../integrations/schemas/repository-environment-config.json").readText()
)
