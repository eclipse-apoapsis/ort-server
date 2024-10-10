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

package org.eclipse.apoapsis.ortserver.config.github

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.read
import io.ktor.utils.io.readRemaining

import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.concurrent.atomic.AtomicInteger

import kotlin.io.use
import kotlin.text.toByteArray
import kotlin.time.Duration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(GitHubConfigFileCache::class.java)

/**
 * The path under which files downloaded from GitHub are stored in the cache. This is a subdirectory of the folder for
 * the specific revision.
 */
private const val FILES_PATH = "tree"

/**
 * The path which stores files listing the content of folders in the cache. This is also a subdirectory under the
 * folder for a specific revision.
 */
private const val FOLDERS_PATH = "folders"

/**
 * A class implementing a file cache for data fetched from the GitHub configuration repository.
 *
 * Caching of content downloaded from GitHub should be rather effective, as the revision can be used as a criterion to
 * construct cache keys. It is guaranteed that the data of a specific revision does not change. By applying caching,
 * the number of requests sent to the GitHub REST API can be reduced significantly. This is especially important since
 * there is a rate limit in place (which is by default 5000 API requests per hour).
 *
 * The implementation has to deal with some race conditions. Since the cache is used by multiple JVMs, it has to be
 * ensured that concurrent access does not cause any problems. For instance, if a file which is not yet present in the
 * cache is requested by two JVM instances at the same time, it should be requested from GitHub only once, and it can
 * be read only after it has been fully downloaded. The implementation achieves this by using exclusive and shared
 * locks on the accessed files.
 *
 * Note: This implementation focuses on the specific requirements of [GitHubConfigFileProvider]. It may be possible to
 * extract common functionality of a file-based cache for more generic use cases later.
 */
internal class GitHubConfigFileCache(
    /** The root directory of the file cache. */
    private val cacheDir: File,

    /**
     * An interval in which the implementation should check for the availability of a read lock. Read locks are used to
     * make sure that a file is fully written before it is read. Instead of doing a blocking wait for the lock, the
     * implementation suspends and checks periodically if the lock is available. So, the thread can be used more
     * effectively.
     */
    private val lockCheckInterval: Duration,

    /**
     * A numeric value which determines how often a cleanup of old cache entries should be performed. A value of
     * *n* means that the cleanup is done on every *nth* invocation of the [cleanup] function.
     */
    private val cleanupRatio: Int,

    /**
     * Defines the maximum age of cache entries before they are removed by a [cleanup] run. This value is compared to
     * the date of the root folder for a specific revision; older revisions are then completely removed.
     */
    private val cleanupMaxAge: Duration
) : GitHubConfigCache {
    private val cleanupCounter = AtomicInteger(0)

    /**
     * Return an [InputStream] to access the file at the given [path] from the given [revision]. If the file is already
     * contained in the cache, a stream to its content can be returned directly. Otherwise, the specified [load]
     * function is called to obtain the file and copy its data into the cache.
     */
    override suspend fun getOrPutFile(
        revision: String,
        path: String,
        load: suspend () -> ByteReadChannel
    ): InputStream {
        logger.info("Request for file '{}' at revision '{}'.", path, revision)

        val dataFile = resolveFileInCache(FILES_PATH, revision, path)
        return getOrPutFileInCache(dataFile, load)
    }

    override suspend fun getOrPutFolderContent(
        revision: String,
        path: String,
        load: suspend () -> Set<String>
    ): Set<String> {
        suspend fun loadFolderContent(): ByteReadChannel {
            val content = load()
            return ByteReadChannel(content.joinToString(System.lineSeparator()).toByteArray())
        }

        logger.info("Request for folder content '{}' at revision '{}'.", path, revision)

        val dataFile = resolveFileInCache(FOLDERS_PATH, revision, path)

        return getOrPutFileInCache(dataFile, ::loadFolderContent).use {
            it.bufferedReader().readLines().toSet()
        }
    }

    override fun cleanup(currentRevision: String) {
        if (cleanupCounter.incrementAndGet() >= cleanupRatio) {
            cleanupCounter.set(0)

            val ageThresholdInstant = Clock.System.now() - cleanupMaxAge
            val ageThreshold = ageThresholdInstant.toEpochMilliseconds()

            logger.info(
                "Performing cleanup of cache directory '{}' on revisions older than {}.",
                cacheDir,
                ageThresholdInstant
            )

            cacheDir.listFiles().orEmpty()
                .filterNot { it.name == currentRevision }
                .filter { it.lastModified() < ageThreshold }
                .forEach { revisionDir ->
                    logger.info(
                        "Removing outdated cache entry for revision '{}' from {}.",
                        revisionDir.name,
                        Instant.fromEpochMilliseconds(revisionDir.lastModified())
                    )
                    if (!revisionDir.deleteRecursively()) {
                        logger.warn("Failed to remove outdated cache entry for revision '{}'.", revisionDir.name)
                    }
                }
        }
    }

    /**
     * Handle the safe request and update of the given [dataFile] in the cache. If the file is not yet present,
     * obtain its content via the given [load] function. Use file locks to make sure that the [load] function is
     * invoked only once for a specific file and that the file is fully written before it is read.
     */
    private suspend fun getOrPutFileInCache(dataFile: File, load: suspend () -> ByteReadChannel): InputStream =
        withContext(Dispatchers.IO) {
            val needRetry = if (!dataFile.isFile) {
                logger.info("File '{}' not found in cache, downloading it now.", dataFile)
                downloadFile(dataFile, load)
            } else {
                false
            }

            if (needRetry) {
                getOrPutFileInCache(dataFile, load)
            } else {
                val stream = dataFile.inputStream().waitForReadLock()
                // There can be the race condition that a process obtains a read lock first before the downloading
                // process acquires the write lock. So, it has to be checked whether the file actually contains data.
                // Otherwise, the operation has to be retried.
                if (dataFile.length() > 0) {
                    stream
                } else {
                    stream.close()
                    getOrPutFileInCache(dataFile, load)
                }
            }
        }

    /**
     * Download a file using the given [load] function and write it into the cache as [dataFile]. Return a flag
     * whether this was successful. A return value of *false* means that the same file is currently downloaded by
     * a different process. Then the operation needs to be retried.
     */
    private suspend fun downloadFile(dataFile: File, load: suspend () -> ByteReadChannel): Boolean =
        withContext(Dispatchers.IO) {
            dataFile.parentFile.mkdirs()

            RandomAccessFile(dataFile, "rw").use { file ->
                file.channel.use { writeChannel ->
                    val lock = runCatching { writeChannel.tryLock() }.getOrNull()

                    // If no write lock can be acquired, the file is currently downloaded by another process. In this
                    // case, simply retry the operation.
                    lock?.use { _ ->
                        if (dataFile.length() <= 0L) {
                            val readChannel = load()

                            while (!readChannel.isClosedForRead) {
                                val packet = readChannel.readRemaining()
                                while (!packet.exhausted()) {
                                    packet.read { writeChannel.write(it) }
                                }
                            }
                        } else {
                            logger.info("File '{}' already exists, skipping download.", dataFile)
                        }
                        false
                    } ?: true
                }
            }
        }

    /**
     * Wait until a read lock is available for the given [FileInputStream]. This is needed to ensure that a file has
     * been fully downloaded until a stream to it is returned from the cache.
     */
    private suspend fun FileInputStream.waitForReadLock(): FileInputStream =
        withContext(Dispatchers.IO) {
            var lock: FileLock?

            do {
                lock = runCatching {
                    this@waitForReadLock.channel.tryLock(0, Long.MAX_VALUE, true)
                }.getOrNull()

                if (lock == null) {
                    delay(lockCheckInterval)
                }
            } while (lock == null)

            lock.close()
            this@waitForReadLock
        }

    /**
     * Resolve a file in the cache based on the given [subFolder], [revision], and [path].
     */
    private fun resolveFileInCache(subFolder: String, revision: String, path: String): File {
        val revisionDir = cacheDir.resolve(revision)
        return revisionDir.resolve(subFolder).resolve(path)
    }
}
