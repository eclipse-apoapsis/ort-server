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

package org.eclipse.apoapsis.ortserver.components.resolutions

import com.github.michaelbull.result.Result
import com.github.michaelbull.result.annotation.UnsafeResultErrorAccess
import com.github.michaelbull.result.annotation.UnsafeResultValueAccess
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getError

import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should

fun <V, E> Result<V, E>.shouldBeOk(block: ((V) -> Unit)): V {
    this should beOk()
    @OptIn(UnsafeResultValueAccess::class)
    return value.also { block(it) }
}

fun <V, E> beOk(): Matcher<Result<V, E>> = beOk(Unit)

fun <V, E> beOk(expected: V): Matcher<Result<V, E>> = OkMatcher(expected)

class OkMatcher<V, E>(val expected: V) : Matcher<Result<V, E>> {
    override fun test(value: Result<V, E>): MatcherResult {
        if (value.isErr) return MatcherResult(false, { "Expected to assert on an Ok, but was $value" }, { "" })
        val actual = value.get()

        if (expected == Unit) return MatcherResult(true, { "" }, { "" })

        return MatcherResult(
            actual == expected,
            { "Result should be Ok($expected), but was Ok($actual)" },
            { "Result should not be Ok, but was Ok($actual)" }
        )
    }
}

fun <V, E> Result<V, E>.shouldBeErr(block: ((E) -> Unit)): E {
    this should beErr()
    @OptIn(UnsafeResultErrorAccess::class)
    return error.also { block(it) }
}

fun <V, E> beErr(): Matcher<Result<V, E>> = beErr(Unit)

fun <V, E> beErr(expected: E): Matcher<Result<V, E>> = ErrMatcher(expected)

class ErrMatcher<V, E>(val expected: E) : Matcher<Result<V, E>> {
    override fun test(value: Result<V, E>): MatcherResult {
        if (value.isOk) return MatcherResult(false, { "Expected to assert on an Err, but was $value" }, { "" })
        val actual = value.getError()

        if (expected == Unit) return MatcherResult(true, { "" }, { "" })

        return MatcherResult(
            actual == expected,
            { "Result should be Err($expected), but was Err($actual)" },
            { "Result should not be Err, but was Err($actual)" }
        )
    }
}
