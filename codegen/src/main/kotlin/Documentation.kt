/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2025 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * sdbus-kotlin. If not, see <https://www.gnu.org/licenses/>.
 */
package com.monkopedia.sdbus

import com.monkopedia.sdbus.Direction.OUT
import com.squareup.kotlinpoet.CodeBlock

/** The conventional annotation carrying documentation in D-Bus introspection XML. */
private const val DOC_STRING = "org.gtk.GDBus.DocString"

/** Its one-line counterpart, rendered as the KDoc summary ahead of the long form. */
private const val DOC_STRING_SHORT = "org.gtk.GDBus.DocString.Short"

/**
 * Width the KDoc text is re-wrapped to. Introspection XML hard-wraps `DocString` values at
 * whatever column the source file used, so the text is unwrapped and re-wrapped rather than
 * copied through.
 */
private const val KDOC_WIDTH = 96

private val BLANK_LINE = Regex("\\n[ \\t]*\\n")
private val WHITESPACE = Regex("\\s+")

/** KDoc for an interface. Interfaces carry documentation only via annotations. */
internal fun Interface.kdoc(): CodeBlock? = kdocOf(annotations.docTexts())

/**
 * KDoc for a method, with an `@param` per input argument and an `@return` for a lone documented
 * output argument. Methods returning several values are collapsed into one generated type, which
 * has no single thing for `@return` to describe, so no tag is emitted for them.
 */
internal fun Method.kdoc(): CodeBlock? = kdocOf(
    descriptions = annotations.docTexts() + doc.docTexts(),
    params = args.filter { it.direction != OUT }.paramDocs(),
    returns = args.filter { it.direction == OUT }.singleOrNull()?.docText()
)

/**
 * KDoc for a signal. [includeParams] distinguishes the adaptor's `on…` emitter, which really does
 * take the signal arguments as parameters, from the proxy's `Flow` property, which does not.
 */
internal fun Signal.kdoc(includeParams: Boolean): CodeBlock? = kdocOf(
    descriptions = annotations.docTexts() + doc.docTexts(),
    params = if (includeParams) args.paramDocs() else emptyList()
)

/** KDoc for a property. */
internal fun Property.kdoc(): CodeBlock? = kdocOf(annotations.docTexts() + doc.docTexts())

/**
 * Assembles the KDoc block, or `null` when the XML documented nothing. Descriptions come first,
 * one paragraph each, followed by the `@param`/`@return` tags.
 */
private fun kdocOf(
    descriptions: List<String>,
    params: List<Pair<String, String>> = emptyList(),
    returns: String? = null
): CodeBlock? {
    val paragraphs = descriptions.flatMap { it.paragraphs() }.map { it.escapedLeadingSigil() }
    val tags = params.map { (name, text) -> "@param $name $text" } +
        listOfNotNull(returns?.let { "@return $it" })
    if (paragraphs.isEmpty() && tags.isEmpty()) return null
    return CodeBlock.builder().apply {
        paragraphs.forEachIndexed { index, paragraph ->
            if (index > 0) add("\n")
            paragraph.wrapped().forEach { add("%L\n", it) }
        }
        if (tags.isNotEmpty() && paragraphs.isNotEmpty()) add("\n")
        tags.forEach { tag -> tag.wrapped().forEach { add("%L\n", it) } }
    }.build()
}

/** The `DocString.Short` summary (if any) followed by the long `DocString`. */
private fun List<Annotation>.docTexts(): List<String> =
    listOfNotNull(docText(DOC_STRING_SHORT), docText(DOC_STRING))

/** The `<doc:doc>` element of the freedesktop doc DTD, the other documentation carrier. */
private fun Doc?.docTexts(): List<String> =
    listOfNotNull(this?.summary?.summaryText, this?.description?.para?.paraContent)

private fun List<Annotation>.docText(name: String): String? = firstOrNull { it.name == name }?.value

/** An argument's documentation collapsed to a single line, for use in an `@param`/`@return` tag. */
private fun Arg.docText(): String? =
    (annotations.docTexts() + doc.docTexts()).flatMap { it.paragraphs() }
        .joinToString(" ")
        .ifEmpty { null }

private fun List<Arg>.paramDocs(): List<Pair<String, String>> = mapIndexedNotNull { index, arg ->
    arg.docText()?.let { (arg.name ?: "arg$index").decapitalCamelCase to it }
}

/** Splits on blank lines, then unwraps each paragraph by collapsing its whitespace. */
private fun String.paragraphs(): List<String> = split(BLANK_LINE)
    .map { it.replace(WHITESPACE, " ").trim() }
    .filter { it.isNotEmpty() }

/**
 * Replaces a GDBus reference sigil that opens a paragraph with its HTML numeric character
 * reference. [tokens] keeps every other sigil off the start of a line by gluing it to the word
 * before it, but the first word of a paragraph has no preceding word to glue to, and a paragraph
 * always starts a line. The entity does not literally start with `@` or `#`, so KDoc no longer
 * reads the paragraph as a block tag or a heading, and Dokka decodes it back to the original
 * character when rendering. This is the same mechanism KotlinPoet already uses on this output to
 * neutralize a comment terminator (`&#42;/`); a Markdown backslash escape is not an option because
 * Dokka emits the backslash literally instead of consuming it.
 */
private fun String.escapedLeadingSigil(): String = when {
    startsWith('@') -> "&#64;" + drop(1)
    startsWith('#') -> "&#35;" + drop(1)
    else -> this
}

/**
 * Splits into wrappable tokens, gluing every GDBus reference sigil (`@argument`, `#Some.Type`) to
 * the word before it. KDoc reads a line-leading `@` as a block tag and a line-leading `#` as a
 * heading, and DocString bodies are full of both, so neither may end up starting a line. A sigil
 * that opens a paragraph has no word to glue to and is replaced by [escapedLeadingSigil] instead.
 */
private fun String.tokens(): List<String> =
    split(' ').fold(mutableListOf<String>()) { tokens, word ->
        if (tokens.isNotEmpty() && (word.startsWith('@') || word.startsWith('#'))) {
            tokens[tokens.lastIndex] = tokens.last() + " " + word
        } else {
            tokens += word
        }
        tokens
    }

/** Greedily re-wraps an unwrapped paragraph to [KDOC_WIDTH]. */
private fun String.wrapped(): List<String> {
    val lines = mutableListOf<String>()
    val line = StringBuilder()
    for (word in tokens()) {
        if (line.isNotEmpty() && line.length + 1 + word.length > KDOC_WIDTH) {
            lines += line.toString()
            line.clear()
        }
        if (line.isNotEmpty()) line.append(' ')
        line.append(word)
    }
    if (line.isNotEmpty()) lines += line.toString()
    return lines
}
