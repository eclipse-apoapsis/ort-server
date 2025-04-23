/*
 * Copyright (C) 2023 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

import org.eclipse.apoapsis.ortserver.config.ConfigManager
import org.eclipse.apoapsis.ortserver.dao.databaseModule

import org.jetbrains.exposed.sql.Database

import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

/**
 * A data class to simulate a connection object in the mock database module.
 */
private data class MockConnection(val success: Boolean)

/**
 * Return a module that is used as replacement for the database module. By checking for the [MockConnection] bean
 * contained in this module, it can be verified whether an endpoint implementation has added the database module.
 */
private fun databaseModuleWithMockConnection() = module {
    single { MockConnection(success = true) }
    single<Database> { mockk() }
}

/**
 * Mock the function that returns the database module to return a module with a mock connection instead. This can be
 * used to test the message handling logic in server endpoints without having to set up a real database. Make sure
 * to call [unmockDatabaseModule] afterward to clear the mocking.
 */
fun mockDatabaseModule() {
    mockkStatic(::databaseModule)
    every { databaseModule() } returns databaseModuleWithMockConnection()
}

/**
 * Reverts mocking of the database module done by [mockDatabaseModule].
 */
fun unmockDatabaseModule() {
    unmockkStatic(::databaseModule)
}

/**
 * Verify in a test whether the current dependency injection configuration contains the (mocked) database module.
 * This can be used in tests together with [mockDatabaseModule] to verify that the database has been configured by
 * the code under test, without the need to have a real database up and running.
 */
fun KoinTest.verifyDatabaseModuleIncluded() {
    val configManager by inject<ConfigManager>()
    configManager shouldNot beNull()

    val mockConnection by inject<MockConnection>()
    mockConnection.success shouldBe true
}

/**
 * Run [block] with a [mocked database module][mockDatabaseModule]. Afterward do cleanup by calling
 * [unmockDatabaseModule]. Note that this is an *inline* function that makes it possible to invoke *suspend* functions
 * in [block].
 */
inline fun withMockDatabaseModule(block: () -> Unit) {
    mockDatabaseModule()
    try {
        block()
    } finally {
        unmockDatabaseModule()
    }
}
