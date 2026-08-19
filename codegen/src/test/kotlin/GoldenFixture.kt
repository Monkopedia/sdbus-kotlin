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

import com.squareup.kotlinpoet.FileSpec
import java.io.File

/**
 * The three sets of golden files each fixture directory under `codegen/src/test/resources` holds,
 * named after the file prefix they are stored under (`interface.0.kt`, `adaptor.0.kt`, ...).
 *
 * Each set has two sides: the default output, and — for the fixtures whose XML carries naming
 * annotations — the `hinted-` output of the same XML with `honorNamingAnnotations` on.
 *
 * [GeneratorTest] and [NamingHintTest] verify the checked-in goldens against this, and
 * `:codegen:regenerateGoldens` rewrites them from this — the single definition keeps the writer and
 * the verifiers in lockstep without either being able to stand in for the other.
 */
internal enum class GoldenFixture(
    val prefix: String,
    private val generator: (Boolean) -> BaseGenerator
) {
    INTERFACE("interface", { InterfaceGenerator(honorNamingAnnotations = it) }),
    ADAPTOR("adaptor", { AdaptorGenerator(honorNamingAnnotations = it) }),
    PROXY("proxy", { ProxyGenerator(honorNamingAnnotations = it) });

    /** The file prefix this kind of golden is stored under, on either side of the flag. */
    fun prefix(hinted: Boolean): String = if (hinted) "$HINTED_PREFIX$prefix" else prefix

    fun generate(testRoot: File, hinted: Boolean = false): List<FileSpec> {
        val xml = parseIntrospectionXml(File(testRoot, "test.xml").readText())
        return generator(hinted).transformXmlToFile(xml).sortedBy { it.name }
    }

    /** The golden files checked in for this fixture, in the order [generate] produces them. */
    fun goldenFiles(testRoot: File, hinted: Boolean = false): List<File> =
        testRoot.listFiles().orEmpty()
            .filter { it.name.startsWith("${prefix(hinted)}.") && it.name.endsWith(".kt") }
            .sortedBy { it.name }

    companion object {
        private const val HINTED_PREFIX = "hinted-"

        /**
         * Whether [testRoot] pins the naming-annotation side of the flag as well as the default
         * one. Only the fixtures whose XML carries naming annotations do; the rest would generate
         * a byte-identical second copy of themselves.
         */
        fun hasHintedGoldens(testRoot: File): Boolean =
            testRoot.listFiles().orEmpty().any { it.name.startsWith(HINTED_PREFIX) }
    }
}
