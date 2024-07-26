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
