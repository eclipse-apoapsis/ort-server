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
import com.charleskorn.kaml.decodeFromStream

import java.io.InputStream

import kotlinx.serialization.DeserializationStrategy

/**
 * A class providing functionality for YAML de-serialization.
 *
 * This class provides a thin wrapper on top of the underlying infrastructure for parsing YAML data. It - and the
 * module it belongs to - was mainly introduced to deal with a version conflict caused by libraries involved in YAML
 * parsing. The problematic libraries are shaded, so that they do not interfere with other parts of the application.
 */
object YamlReader {
    /**
     * Deserialize the data in the given [stream] to a model object of type [T] using the given [deserializer].
     */
    fun <T> decodeFromStream(deserializer: DeserializationStrategy<T>, stream: InputStream): T =
        Yaml.default.decodeFromStream(deserializer, stream)
}
