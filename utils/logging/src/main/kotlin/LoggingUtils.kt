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

package org.eclipse.apoapsis.ortserver.utils.logging

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext

import org.slf4j.MDC

/**
 * A wrapper for [kotlinx.coroutines.runBlocking] which always adds an [MDCContext] to the newly created coroutine
 * context. This ensures that SLF4J's MDC context is not lost when `runBlocking` is used.
 *
 * This function should be used instead of [kotlinx.coroutines.runBlocking] in all code to ensure that the MDC context
 * is always preserved.
 */
fun <T> runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T =
    @Suppress("ForbiddenMethodCall")
    kotlinx.coroutines.runBlocking(context + MDCContext()) { block() }

/**
 * Call the specified [block] with the given [elements] in the [MDC coroutine context][MDCContext] and return its value.
 * If the provided [elements] overwrite any existing values, the previous values are restored after executing [block].
 */
suspend fun <T> withMdcContext(vararg elements: Pair<String, String>, block: suspend CoroutineScope.() -> T): T {
    val oldContext = MDC.getCopyOfContextMap()

    elements.forEach { (key, value) -> MDC.put(key, value) }

    return try {
        withContext(MDCContext(), block)
    } finally {
        if (oldContext == null) {
            MDC.clear()
        } else {
            MDC.setContextMap(oldContext)
        }
    }
}
