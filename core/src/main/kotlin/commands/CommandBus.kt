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

package org.eclipse.apoapsis.ortserver.core.commands

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

interface CommandMiddleware {
    suspend fun <RETURN_VALUE> intercept(
        command: Command<RETURN_VALUE>,
        next: suspend (Command<RETURN_VALUE>) -> Result<RETURN_VALUE>
    ): Result<RETURN_VALUE>
}

class LoggingMiddleware : CommandMiddleware {
    override suspend fun <RETURN_VALUE> intercept(
        command: Command<RETURN_VALUE>,
        next: suspend (Command<RETURN_VALUE>) -> Result<RETURN_VALUE>
    ): Result<RETURN_VALUE> {
        println("Executing command: $command")
        return next(command)
    }
}

class CommandBus {
    private val handlers = mutableMapOf<KClass<*>, CommandHandler<*, *>>()
    private val middlewares = mutableListOf<CommandMiddleware>()
    private val mutex = Mutex()

    suspend fun <COMMAND : Command<RETURN_VALUE>, RETURN_VALUE> registerHandler(
        commandClass: KClass<COMMAND>,
        handler: CommandHandler<COMMAND, RETURN_VALUE>
    ) {
        mutex.withLock {
            handlers[commandClass] = handler
        }
    }

    suspend fun addMiddleware(middleware: CommandMiddleware) {
        mutex.withLock {
            middlewares.add(middleware)
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <RETURN_VALUE> dispatch(command: Command<RETURN_VALUE>): Result<RETURN_VALUE> {
        val handler = mutex.withLock {
            handlers[command::class] as? CommandHandler<Command<RETURN_VALUE>, RETURN_VALUE>
        } ?: return Result.failure(
            IllegalArgumentException("No handler found for command: ${command::class.java.name}")
        )

        val execute: suspend (Command<RETURN_VALUE>) -> Result<RETURN_VALUE> = { cmd -> handler.execute(cmd) }

        return middlewares.foldRight(execute) { middleware, next ->
            { cmd -> middleware.intercept(cmd, next) }
        }(command)
    }
}
