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

package org.eclipse.apoapsis.ortserver.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.modifiedText

import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/** The default of detekt's `MaxLineLength` rule, used if its configuration cannot be found. */
private const val DEFAULT_MAX_LINE_LENGTH = 120

/**
 * Reports KDoc comments that span multiple lines although their content is a single line that would fit into a
 * single-line KDoc without exceeding the maximum line length. The maximum line length is taken from the configuration
 * of detekt's `style > MaxLineLength` rule.
 */
class SingleLineKDoc(config: Config) : Rule(
    config,
    "Reports multi-line KDoc comments that fit on a single line"
) {
    private val maxLineLength: Int by lazy {
        // Navigate from this rule's configuration via the rule set's configuration to the root configuration.
        config.parent?.parent?.subConfig("style")?.subConfig("MaxLineLength")?.valueOrNull<Int>("maxLineLength")
            ?: DEFAULT_MAX_LINE_LENGTH
    }

    override fun visit(root: KtFile) {
        val replacements = root.findSingleLineKDocReplacements()
        if (replacements.isEmpty()) return

        replacements.keys.forEach { kdoc ->
            report(Finding(Entity.from(kdoc), "KDoc fits on a single line and should be written as `/** ... */`."))
        }

        if (autoCorrect) {
            // Other rules might already have modified the file, so work on the latest text.
            val file = root.modifiedText?.let { KtPsiFactory(root.project).createFile(root.name, it) } ?: root
            root.modifiedText = file.replaceKDocs(file.findSingleLineKDocReplacements())
        }
    }

    private fun KtFile.findSingleLineKDocReplacements(): Map<KDoc, String> =
        collectDescendantsOfType<KDoc>().mapNotNull { kdoc -> kdoc.toSingleLine()?.let { kdoc to it } }.toMap()

    /**
     * Return the single-line representation of this KDoc, or null if it already is a single line, has more than one
     * line of content, contains block tags, or would exceed the maximum line length.
     */
    private fun KDoc.toSingleLine(): String? {
        if ('\n' !in text) return null

        val content = text.removePrefix("/**").removeSuffix("*/").lines()
            .map { it.trim().removePrefix("*").trim() }
            .filter { it.isNotEmpty() }
            .singleOrNull() ?: return null

        if (content.startsWith("@")) return null

        val singleLine = "/** $content */"

        val fileText = containingFile.text
        val lineStart = fileText.lastIndexOf('\n', textRange.startOffset - 1) + 1
        val lineEnd = fileText.indexOf('\n', textRange.endOffset).takeUnless { it < 0 } ?: fileText.length
        val lineLength = textRange.startOffset - lineStart + singleLine.length + lineEnd - textRange.endOffset

        return singleLine.takeIf { lineLength <= maxLineLength }
    }
}

private fun KtFile.replaceKDocs(replacements: Map<KDoc, String>): String =
    StringBuilder(text).apply {
        replacements.entries.sortedByDescending { it.key.textRange.startOffset }.forEach { (kdoc, replacement) ->
            replace(kdoc.textRange.startOffset, kdoc.textRange.endOffset, replacement)
        }
    }.toString()
