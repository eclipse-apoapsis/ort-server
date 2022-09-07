/*
 * Copyright (C) 2022 The ORT Project Authors (See <https://github.com/oss-review-toolkit/ort-server/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.server.shared.models.api.common

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A property type that can be used for PATCH requests. It represents three different states:
 * * [OptionalValue.Present]
 * * [OptionalValue.Absent]
 * * [OptionalValue.Null]
 */
@Serializable(with = OptionalValueSerializer::class)
sealed interface OptionalValue<out T> {
    /**
     * Value is present, the entry will be updated with [value].
     */
    class Present<T>(val value: T): OptionalValue<T> {
        override fun toString() = value.toString()
    }

    /**
     * Omitted from the request, will be ignored in the update.
     */
    object Absent: OptionalValue<Nothing>

    /**
     * Explicitly set to null, will result in deleting the value.
     */
    object Null: OptionalValue<Nothing>

    /**
     * Execute [function] if this value is [Present].
     */
    fun ifPresent(function: (T) -> Unit) {
        if (this is Present) function(value)
    }

    /**
     * Execute [function] if this value is not [Absent].
     */
    fun ifNotAbsent(function: (T?) -> Unit) {
        when (this) {
            is Present -> function(value)
            is Null -> function(null)
            else -> return
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
class OptionalValueSerializer<T>(private val valueSerializer: KSerializer<T>) : KSerializer<OptionalValue<T>> {
    override val descriptor: SerialDescriptor = valueSerializer.descriptor

    override fun deserialize(decoder: Decoder): OptionalValue<T> {
        val value = valueSerializer.deserialize(decoder)
        return if (value != null) {
            OptionalValue.Present(value)
        } else {
            OptionalValue.Null
        }
    }

    override fun serialize(encoder: Encoder, value: OptionalValue<T>) {
        when (value) {
            is OptionalValue.Absent -> {}
            is OptionalValue.Null -> encoder.encodeNull()
            is OptionalValue.Present -> valueSerializer.serialize(encoder, value.value)
        }
    }
}
