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

package org.eclipse.apoapsis.ortserver.dao

import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/**
 * Disable foreign key constraints for the given [block]. This is useful in testing when otherwise a lot of irrelevant
 * test data would have to be created to satisfy foreign key constraints.
 */
fun <T> Transaction.disableForeignKeyConstraints(block: (Transaction.() -> T)): T {
    TransactionManager.current().exec("SET session_replication_role = 'replica';")
    val result = block()
    TransactionManager.current().exec("SET session_replication_role = 'origin';")
    return result
}
