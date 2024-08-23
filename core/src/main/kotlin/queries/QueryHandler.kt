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

package org.eclipse.apoapsis.ortserver.core.queries

/**
 * An interface for query handlers. Query handlers are used to execute a [Query]. They return the result of the query,
 * which is specified by the [RETURN_VALUE] type parameter.
 */
interface QueryHandler<QUERY : Query<RETURN_VALUE>, RETURN_VALUE> {
    /**
     * Execute the [query]. This function must not throw exceptions and instead encapsulate them in the result.
     */
    suspend fun execute(query: QUERY): Result<RETURN_VALUE>
}
