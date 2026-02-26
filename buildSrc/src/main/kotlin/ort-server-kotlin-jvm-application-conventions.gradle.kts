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

plugins {
    // Apply precompiled plugins.
    id("ort-server-kotlin-jvm-conventions")

    // Apply third-party plugins.
    alias(libs.plugins.tinyJib)
}

dependencies {
    implementation(enforcedPlatform(libs.kotlinBom))
}

tinyJib {
    // Support Jib system properties for compatibility.
    System.getProperty("jib.allowInsecureRegistries")?.also { allowInsecureRegistries = it.toBooleanStrict() }
    System.getProperty("jib.applicationCache")?.also { applicationCache = File(it) }
    System.getProperty("jib.baseImageCache")?.also { baseImageCache = File(it) }

    System.getProperty("jib.container.labels")?.also {
        container.labels = it.split(',').associate { label -> label.substringBefore('=') to label.substringAfter('=') }
    }

    System.getProperty("jib.from.platforms")?.also {
        from.platforms {
            it.split(',').map { platform ->
                platform {
                    os = platform.substringBefore('/')
                    architecture = platform.substringAfter('/')
                }
            }
        }
    }

    System.getProperty("jib.from.image")?.also { from.image = it }
    System.getProperty("jib.to.image")?.also { to.image = it }

    System.getProperty("jib.to.auth.username")?.also { to.auth.username = it }
    System.getProperty("jib.to.auth.password")?.also { to.auth.password = it }

    System.getProperty("jib.to.tags")?.also { to.tags = it.split(',') }
}
