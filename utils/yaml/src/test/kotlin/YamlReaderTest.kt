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

package org.eclipse.apoapsis.ortserver.utils.yaml

import com.charleskorn.kaml.Yaml

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.ByteArrayInputStream

import org.eclipse.apoapsis.ortserver.api.v1.model.Organization

class YamlReaderTest : WordSpec({
    "decodeFromStream" should {
        "deserialize an object from a YAML stream" {
            val organization = Organization(
                id = 42,
                name = "Test organization",
                description = "YAML serialization tests"
            )
            val yaml = Yaml.default.encodeToString(Organization.serializer(), organization)

            val stream = ByteArrayInputStream(yaml.toByteArray())
            val deserializedOrganization = YamlReader.decodeFromStream(Organization.serializer(), stream)

            deserializedOrganization shouldBe organization
        }
    }
})
