package org.eclipse.apoapsis.ortserver.dao.repositories

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.eclipse.apoapsis.ortserver.dao.blockingQuery

import org.eclipse.apoapsis.ortserver.dao.test.DatabaseTestExtension

import java.sql.Connection

class DaoAnalyzerRunRepositoryLoadTest : StringSpec({

    val dbExtension = extension(DatabaseTestExtension())

    "AnalyzerRunRepository.create should handle concurrent requests" {
        val runCount = 50
        val repository = dbExtension.fixtures.createRepository()
        (1..runCount).map { idx ->
            async(Dispatchers.IO) {
                val ortRun =
                    dbExtension.db.blockingQuery(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE) {
                        maxAttempts = 25
                        dbExtension.fixtures.createOrtRun(repositoryId = repository.id)
                    }
                val createdAnalyzerRun = dbExtension.db.blockingQuery {
                    val analyzerJob = dbExtension.fixtures.createAnalyzerJob(ortRun.id)
                    dbExtension.fixtures.analyzerRunRepository.create(analyzerJob.id, analyzerRun)
                }

                val dbEntry =  dbExtension.fixtures.analyzerRunRepository.get(createdAnalyzerRun.id)

                dbEntry.shouldNotBeNull()
                dbEntry shouldBe analyzerRun.copy(id = createdAnalyzerRun.id, analyzerJobId = createdAnalyzerRun.analyzerJobId)
            }
        }.awaitAll()
    }
})
