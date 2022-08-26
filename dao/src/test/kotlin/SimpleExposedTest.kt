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

package org.ossreviewtoolkit.server.dao.test

import io.kotest.matchers.collections.shouldHaveSize

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

import org.ossreviewtoolkit.server.dao.connect
import org.ossreviewtoolkit.server.dao.tables.LicenseStringsTable
import org.ossreviewtoolkit.server.utils.test.DatabaseTest

import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(SimpleExposedTest::class.java)

/**
 * A simple test to check the configuration of Exposed/PostgreSQL driver in the 'dao' module.
 */
class SimpleExposedTest : DatabaseTest() {
    init {
        dataSource.connect()

        test("Exposed should interact with a PostgreSQL database") {
            transaction {
                val mitId = LicenseStringsTable.insert {
                    it[name] = "MIT"
                } get LicenseStringsTable.id
                log.info("License String has been created with ID '$mitId'.")

                val licenseStrings = LicenseStringsTable.selectAll()
                licenseStrings shouldHaveSize 1
                val licenseString = licenseStrings.single()

                log.info("License String: $licenseString")
            }
        }
    }
}
