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

import java.util.concurrent.Executors

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * A [CoroutineDispatcher] backed by [Java Virtual Threads][Executors.newVirtualThreadPerTaskExecutor].
 *
 * Designed as a lightweight, scalable alternative to [Dispatchers.IO] for offloading synchronous or blocking I/O calls
 * (e.g., third-party Java SDKs, JDBC, network, and file system operations).
 *
 * ### Key Advantages
 * - **Low Memory Footprint:** Spawns a virtual thread per task instead of allocating heavy OS platform threads.
 * - **Eliminates Thread Starvation:** Unbounded concurrency prevents slow I/O calls from blocking other coroutines.
 * - **Safe `synchronized` Handling:** On JDK 24+, virtual threads automatically unmount inside `synchronized` blocks
 *   during I/O without pinning platform carrier threads ([JEP 491](https://openjdk.org/jeps/491)).
 *
 * ### Limitations & When NOT to Use
 * - **CPU-Bound Workload:** Do not use for intensive computations; use [Dispatchers.Default] instead.
 * - **Native / JNI Code:** Long-running calls into C/C++ native code via JNI/FFI will pin the underlying carrier
 *   thread. Use [Dispatchers.IO] for these edge cases.
 * - **Unthrottled Concurrency:** Because virtual threads do not limit concurrent executions, use
 *   [CoroutineDispatcher.limitedParallelism] when wrapping third-party APIs with strict connection or throughput
 *   limits.
 */
val Dispatchers.Virtual: CoroutineDispatcher by lazy {
    Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
}
