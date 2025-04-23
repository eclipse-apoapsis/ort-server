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

package org.eclipse.apoapsis.ortserver.transport

import org.eclipse.apoapsis.ortserver.utils.system.ORT_SERVER_VERSION

import org.slf4j.Logger

private const val ORT_SERVER_BANNER = """
  ___    ____    _____           ____                                       
 / _ \  |  _ \  |_   _|         / ___|    ___   _ __  __   __   ___   _ __  
| | | | | |_) |   | |    _____  \___ \   / _ \ | '__| \ \ / /  / _ \ | '__| 
| |_| | |  _ <    | |   |_____|  ___) | |  __/ | |     \ V /  |  __/ | |    
 \___/  |_| \_\   |_|           |____/   \___| |_|      \_/    \___| |_|    
 --------------------------------------------------------------------------
 ORT Server version: $ORT_SERVER_VERSION
"""

/**
 * Log the ORT Server version including the commit hash. Also display a pretty Ascii art banner.
 */
fun Logger.logVersion() {
    info(ORT_SERVER_BANNER)
}
