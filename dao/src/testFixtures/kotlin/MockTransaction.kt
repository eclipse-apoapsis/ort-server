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

package org.eclipse.apoapsis.ortserver.dao.test

import io.mockk.every
import io.mockk.invoke
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic

import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction

private const val TRANSACTION_MANAGER_CLASS = "org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManagerKt"

/**
 * Create a static mock for [transaction] that does not actually create a transaction. Can be used to mock database
 * access for functions that call [transaction]. Make sure to call [unmockkTransaction] to clear the mock.
 */
fun mockkTransaction() {
    val slot = slot<Transaction.() -> Any>()

    mockkObject(TransactionManager.Companion)
    every { TransactionManager.managerFor(any()) } returns mockk {
        every { defaultIsolationLevel } returns -1
        every { defaultReadOnly } returns false
        every { defaultMaxAttempts } returns -1
    }

    mockkStatic(TRANSACTION_MANAGER_CLASS)
    every { transaction(any(), capture(slot)) } answers { slot.invoke(mockk()) }
    every { transaction(any(), any(), any(), capture(slot)) } answers { slot.invoke(mockk()) }
}

/**
 * Clear the static mock created by [mockkTransaction].
 */
fun unmockkTransaction() {
    unmockkObject(TransactionManager.Companion)
    unmockkStatic(TRANSACTION_MANAGER_CLASS)
}

/**
 * Create a static mock for [transaction] that does not actually create a transaction. Can be used to mock database
 * access for functions that call [transaction]. Clears the mock after executing [block]. Note that this is an
 * *inline* function that makes it possible to invoke *suspend* functions in [block].
 */
inline fun <T> mockkTransaction(block: () -> T): T {
    mockkTransaction()
    return try {
        block()
    } finally {
        unmockkTransaction()
    }
}
