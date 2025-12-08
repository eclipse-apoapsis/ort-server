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

package org.eclipse.apoapsis.ortserver.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

@KeepGeneratedSerializer
@OptIn(ExperimentalSerializationApi::class)
@Serializable(with = ResolvableSecretSerializer::class)
data class ResolvableSecret(
    /** The name of the secret. */
    val name: String,

    /** The source of the secret. */
    val source: SecretSource
)

/**
 * A custom deserializer for backward compatibility that allows deserializing a [ResolvableSecret] from either a string
 * or an object. If the value is deserialized from a string, the source is set to [SecretSource.ADMIN] because that was
 * the only available source before the introduction of [ResolvableSecret].
 */
object ResolvableSecretSerializer : KSerializer<ResolvableSecret> by ResolvableSecret.generatedSerializer() {
    override fun deserialize(decoder: Decoder): ResolvableSecret {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("ResolvableSecret can only be deserialized from JSON.")

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                if (!element.isString) {
                    throw SerializationException("Expected string or object for ResolvableSecret.")
                }
                ResolvableSecret(
                    name = element.content,
                    source = SecretSource.ADMIN
                )
            }

            else -> {
                jsonDecoder.json.decodeFromJsonElement(ResolvableSecret.generatedSerializer(), element)
            }
        }
    }
}
