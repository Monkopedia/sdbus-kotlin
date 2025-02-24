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
import nl.adaptivity.xmlutil.serialization.XML
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class GeneratorTest {

    @ParameterizedTest
    @MethodSource("data")
    fun testInterface(testRoot: File) {
        val xmlStr = File(testRoot, "test.xml").readText()
        val xml = XML.decodeFromString<XmlRootNode>(xmlStr)
        val gen = InterfaceGenerator().transformXmlToFile(xml).sortedBy { it.name }
        assertFiles(testRoot, "interface", gen)
    }

    @ParameterizedTest
    @MethodSource("data")
    fun testAdaptor(testRoot: File) {
        val xmlStr = File(testRoot, "test.xml").readText()
        val xml = XML.decodeFromString<XmlRootNode>(xmlStr)
        val gen = AdaptorGenerator().transformXmlToFile(xml).sortedBy { it.name }
        assertFiles(testRoot, "adaptor", gen)
    }

    @ParameterizedTest
    @MethodSource("data")
    fun testProxy(testRoot: File) {
        val xmlStr = File(testRoot, "test.xml").readText()
        val xml = XML.decodeFromString<XmlRootNode>(xmlStr)
        val gen = ProxyGenerator().transformXmlToFile(xml).sortedBy { it.name }
        assertFiles(testRoot, "proxy", gen)
    }

    private fun assertFiles(root: File, prefix: String, actual: List<FileSpec>) {
        if (WRITE_FILES) {
            val writeRoot = File("src/test/resources/${root.name}")
            actual.forEachIndexed { index, fileSpec ->
                File(writeRoot, "$prefix.$index.kt").writeText(fileSpec.toString())
            }
        }
        val expected = root.listFiles().orEmpty()
            .filter { it.name.startsWith("$prefix.") && it.name.endsWith(".kt") }
            .sortedBy { it.name }
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expected, actual) ->
            val expectedContent = expected.readText()
            val actualContent = actual.toString()
            assertEquals(expectedContent, actualContent)
        }
    }

    companion object {
        @JvmStatic
        fun data(): Iterable<Array<Any>> =
            GeneratorTest::class.java.getResource("/BluezAdapter1Test")
                ?.file
                ?.let {
                    File(it).parentFile.listFiles()
                }.orEmpty()
                .map { arrayOf(it) }

        private const val WRITE_FILES = false
    }
}
