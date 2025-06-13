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

package org.eclipse.apoapsis.ortserver.shared.apimodel

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A property type that can be used in PATCH requests. It represents two different states:
 * * [OptionalValue.Present]
 * * [OptionalValue.Absent]
 *
 * This make generic PATCH requests possible that take all upgradable properties as parameters and only update the
 * ones that are [present][OptionalValue.Present]. Otherwise, for nullable properties, there would be no way to
 * distinguish if the property should be ignored or updated.
 */
@Serializable(with = OptionalValueSerializer::class)
sealed interface OptionalValue<out T> {
    /**
     * Value is present, the property will be updated with [value].
     */
    class Present<T>(val value: T) : OptionalValue<T> {
        override fun toString() = value.toString()
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null) return false
            if (this::class != other::class) return false

            other as Present<*>

            return value == other.value
        }

        override fun hashCode(): Int {
            return value?.hashCode() ?: 0
        }
    }

    /**
     * Value is not present, the property will be ignored.
     */
    object Absent : OptionalValue<Nothing>

    /**
     * Execute [function] if this value is [Present].
     */
    fun ifPresent(function: (T) -> Unit) {
        if (this is Present) function(value)
    }

    /**
     * Execute [function] if this value is [Absent].
     */
    fun ifAbsent(function: () -> Unit) {
        when (this) {
            is Absent -> function()
            else -> return
        }
    }

    /**
     * If this [OptionalValue] is [Present], [transform] the [value][Present.value], otherwise return [Absent].
     */
    fun <M> map(transform: (T) -> M) =
        when (this) {
            is Present -> Present(transform(value))
            else -> Absent
        }
}

/**
 * Return the [value][Present.value] if this [OptionalValue] is [Present], otherwise throw an
 * [IllegalArgumentException].
 */
val <T> OptionalValue<T>.valueOrThrow: T get() {
    require(this is OptionalValue.Present)
    return value
}

class OptionalValueSerializer<T>(private val valueSerializer: KSerializer<T>) : KSerializer<OptionalValue<T>> {
    override val descriptor: SerialDescriptor = valueSerializer.descriptor

    override fun deserialize(decoder: Decoder): OptionalValue<T> {
        val value = valueSerializer.deserialize(decoder)
        return value.asPresent()
    }

    override fun serialize(encoder: Encoder, value: OptionalValue<T>) {
        when (value) {
            is OptionalValue.Absent -> {}
            is OptionalValue.Present -> valueSerializer.serialize(encoder, value.value)
        }
    }
}

/**
 * Wrap [this] object in [OptionalValue.Present].
 */
fun <T : Any?> T.asPresent() = OptionalValue.Present(this)
