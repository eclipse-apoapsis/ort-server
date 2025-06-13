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

package org.eclipse.apoapsis.ortserver.core.plugins

import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.serialization
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

import org.eclipse.apoapsis.ortserver.shared.apimodel.OptionalValue
import org.eclipse.apoapsis.ortserver.shared.apimodel.OptionalValueSerializer

import org.koin.ktor.ext.inject

fun Application.configureSerialization() {
    val json: Json by inject()

    install(ContentNegotiation) {
        serialization(ContentType.Application.Json, json)
    }
}

val customSerializersModule = SerializersModule {
    contextual(OptionalValue::class) { args -> OptionalValueSerializer(args[0]) }
}
