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

package org.eclipse.apoapsis.ortserver.utils.logging

import ch.qos.logback.classic.encoder.JsonEncoder
import ch.qos.logback.classic.spi.IThrowableProxy
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.encoder.JsonEscapeUtil

private const val THROWABLE_ATTR_NAME = "throwable"

/**
 * A [JsonEncoder] that renders a throwable as a single top-level [THROWABLE_ATTR_NAME] string field instead of the
 * nested object produced by the base class. The string is identical to the output of the classic pattern layout (the
 * `%ex` conversion word), making the stack trace easy to read in JSON logs and easy to consume in external tooling.
 */
class OrtServerJsonEncoder : JsonEncoder() {
    override fun appendThrowableProxy(sb: StringBuilder, attributeName: String?, itp: IThrowableProxy?) {
        if (itp == null) return

        sb.append(',')

        appenderMember(
            sb,
            org.eclipse.apoapsis.ortserver.utils.logging.THROWABLE_ATTR_NAME,
            JsonEscapeUtil.jsonEscapeString(ThrowableProxyUtil.asString(itp))
        )
    }
}
