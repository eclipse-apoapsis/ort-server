/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

package org.eclipse.apoapsis.ortserver.shared.coroutines

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/**
 * Call [withContext] with the given [context] and [block] and return the [AutoCloseable] resource created by the
 * [block], making sure that the resource is closed if it cannot be handed over to the caller.
 *
 * Due to the prompt cancellation guarantee of [withContext], the result of the [block] is discarded if the calling
 * coroutine is canceled while the block completes, and a [CancellationException] is thrown instead. For resources like
 * streams this would leak the resource, as the caller never gets the chance to close it. This function closes the
 * resource in this case before rethrowing the exception.
 */
suspend fun <T : AutoCloseable> withContextClosingOnCancel(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T {
    var result: T? = null

    try {
        return withContext(context) { block().also { result = it } }
    } catch (e: CancellationException) {
        result?.let { resource -> runCatching { resource.close() }.onFailure { e.addSuppressed(it) } }
        throw e
    }
}
