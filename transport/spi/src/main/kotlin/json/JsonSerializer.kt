/*
 * Copyright (C) 2022 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.transport.json

import kotlin.reflect.KClass

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * An interface supporting the JSON serialization and deserialization of a specific type.
 *
 * The main purpose of this interface is to abstract away from some details of Kotlin serialization that arise when
 * dealing with arbitrary types.
 */
interface JsonSerializer<T> {
    companion object {
        /**
         * Return an instance of [JsonSerializer] that can handle instances of the provided data type.
         */
        inline fun <reified T : Any> forType(): JsonSerializer<T> = forClass(T::class)

        /**
         * Return an instance of [JsonSerializer] that can handle instances of the provided [class][clazz].
         */
        @OptIn(InternalSerializationApi::class)
        fun <T : Any> forClass(clazz: KClass<T>): JsonSerializer<T> {
            val serializer = clazz.serializer()

            return object : JsonSerializer<T> {
                override fun toJson(obj: T): String = Json.encodeToString(serializer, obj)

                override fun fromJson(json: String): T = Json.decodeFromString(serializer, json)
            }
        }
    }

    /**
     * Generate a JSON string representation for [obj].
     */
    fun toJson(obj: T): String

    /**
     * Deserialize the given [json] string to an object of the managed type.
     */
    fun fromJson(json: String): T
}
