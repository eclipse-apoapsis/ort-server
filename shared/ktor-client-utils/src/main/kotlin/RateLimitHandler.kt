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

package org.eclipse.apoapsis.ortserver.shared.ktorclientutils

import com.typesafe.config.Config

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpStatusCode

import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.delay

import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(RateLimitConfig::class.java)

/**
 * An interface defining a strategy to compute the delay for the next retry when handling a failed HTTP response due to
 * rate limiting.
 */
interface RateLimitDelayStrategy {
    /**
     * Return a delay until the next retry of the current HTTP request. This function is called when an HTTP request
     * yields a 429 response code and the maximum number of retries is not yet reached and no `Retry-After` header
     * is available. An implementation can compute a suitable delay based on the provided [retryCount] and [config].
     * The logic handling the 429 failure makes sure that the returned delay does not exceed the configured maximum
     * delay.
     */
    fun computeDelay(retryCount: Int, config: RateLimitConfig): Duration
}

/**
 * An enumeration class providing different standard implementations of [RateLimitDelayStrategy].
 */
enum class StandardRateLimitStrategies : RateLimitDelayStrategy {
    /**
     * A strategy that returns the constant [RateLimitConfig.defaultDelay] value for all retry counts.
     */
    CONSTANT {
        override fun computeDelay(
            retryCount: Int,
            config: RateLimitConfig
        ): Duration = config.defaultDelay
    },

    /**
     * A strategy implementing an exponential backoff algorithm. Starting with [RateLimitConfig.defaultDelay], for
     * each retry, the delay is doubled.
     */
    BACKOFF {
        override fun computeDelay(
            retryCount: Int,
            config: RateLimitConfig
        ): Duration = (config.defaultDelay.inWholeMilliseconds * (2.0.pow(retryCount - 1))).milliseconds
    },

    /**
     * A strategy that computes a delay until the beginning of the next minute. This can be used to deal with rate
     * limiting that permits a given number of requests per minute.
     */
    NEXT_MINUTE {
        override fun computeDelay(
            retryCount: Int,
            config: RateLimitConfig
        ): Duration {
            val secondsInMinute = Clock.System.now().epochSeconds % 60
            return (60 - secondsInMinute).seconds
        }
    }
}

/**
 * Configuration for the rate limiting behavior of an HTTP client.
 */
data class RateLimitConfig(
    /** Maximum number of retry attempts after a 429 response. */
    val maxRetries: Int,

    /** Default delay between retries. */
    val defaultDelay: Duration,

    /**
     * Upper bound for any computed delay to prevent excessive waiting, e.g. when the server sends an
     * unreasonably large [Retry-After](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Retry-After) value
     * or a backoff value gets too high.
     */
    val maxDelay: Duration,

    /**
     * The strategy to compute the delay between retries. This allows for a flexible way to compute delays, so that the
     * behavior can be adapted to many use cases.
     */
    val delayStrategy: RateLimitDelayStrategy
) {
    companion object {
        /** The name of the path under which configuration options for rate limiting are expected. */
        const val CONFIG_PATH = "rateLimit"

        /** The configuration property for the [RateLimitConfig.maxRetries] option. */
        const val MAX_RETRIES_PROPERTY = "maxRetries"

        /**
         * The configuration property for the [RateLimitConfig.defaultDelay] option (value expected in milliseconds).
         */
        const val DEFAULT_DELAY_PROPERTY = "defaultDelayMs"

        /**
         * The configuration property for the [RateLimitConfig.maxDelay] option (value expected in milliseconds).
         */
        const val MAX_DELAY_PROPERTY = "maxDelayMs"

        /**
         * The configuration property for the [RateLimitConfig.delayStrategy] option (value expected as the name of a
         * [StandardRateLimitStrategies] enum.
         */
        const val DELAY_STRATEGY_PROPERTY = "delayStrategy"

        /**
         * Return a [RateLimitConfig] instance that has been initialized from the values of the given [config].
         */
        fun create(config: Config): RateLimitConfig {
            val rateLimitConfig = config.getConfig(CONFIG_PATH)

            val maxRetries = rateLimitConfig.getInt(MAX_RETRIES_PROPERTY)
            val defaultDelay = rateLimitConfig.getLong(DEFAULT_DELAY_PROPERTY).milliseconds
            val maxDelay = rateLimitConfig.getLong(MAX_DELAY_PROPERTY).milliseconds
            val delayStrategy = StandardRateLimitStrategies.valueOf(
                rateLimitConfig.getString(DELAY_STRATEGY_PROPERTY)
            )

            return RateLimitConfig(maxRetries, defaultDelay, maxDelay, delayStrategy)
        }
    }
}

/**
 * Install an interceptor that implements a standard handling for requests answered with HTTP 429 (Too Many Requests)
 * according to the given [config].
 *
 * On a 429 response the interceptor:
 * 1. Reads the `Retry-After` response header (interpreted as seconds) if present, capped at [RateLimitConfig.maxDelay].
 * 2. Falls back to the [RateLimitDelayStrategy] defined in the configuration to compute the delay before the next
 *    retry. The [RateLimitConfig.maxDelay] property is applied to the result as well.
 * 3. Stops retrying after [RateLimitConfig.maxRetries] attempts and returns the last 429 response to the caller.
 */
fun HttpClient.withRateLimitHandling(config: RateLimitConfig): HttpClient {
    plugin(HttpSend).intercept { request ->
        var call = execute(request)
        var retryCount = 0

        while (call.response.status == HttpStatusCode.TooManyRequests && retryCount < config.maxRetries) {
            retryCount++

            val delay = resolveDelay(
                retryAfterHeader = call.response.headers["Retry-After"],
                retryCount = retryCount,
                config = config
            )

            logger.warn(
                "Rate limit exceeded (HTTP 429). Waiting $delay before retry " +
                        "$retryCount/${config.maxRetries}."
            )

            delay(delay)
            call = execute(request)
        }

        if (call.response.status == HttpStatusCode.TooManyRequests) {
            logger.error(
                "Rate limit still exceeded after ${config.maxRetries} retries. Giving up."
            )
        }

        call
    }

    return this
}

/**
 * Compute the delay in milliseconds before the next retry based on the given [retryAfterHeader], the [retryCount],
 * and the [config]. The header takes precedence if it is set. Otherwise, delegate to the [RateLimitDelayStrategy]
 * defined in the configuration.
 */
internal fun resolveDelay(retryAfterHeader: String?, retryCount: Int, config: RateLimitConfig): Duration {
    val fromHeader = retryAfterHeader?.toLongOrNull()?.seconds

    val delay = fromHeader ?: config.delayStrategy.computeDelay(retryCount, config)

    return delay.coerceAtMost(config.maxDelay)
}
