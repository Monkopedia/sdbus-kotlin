package com.monkopedia.sdbus

import java.io.File
import kotlinx.serialization.decodeFromString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The opt-in half of [GeneratorTest]. It runs over the same fixture directories with naming
 * annotations honored and compares against the `hinted-` goldens, so a fixture that carries both
 * `interface.0.kt` and `hinted-interface.0.kt` pins both sides of the flag from one XML.
 *
 * There is deliberately no write-the-goldens switch here — the goldens are only ever compared.
 */
class NamingHintTest {

    @ParameterizedTest
    @MethodSource("data")
    fun testInterface(testRoot: File) =
        assertHinted(testRoot, "interface", InterfaceGenerator(honorNamingAnnotations = true))

    @ParameterizedTest
    @MethodSource("data")
    fun testAdaptor(testRoot: File) =
        assertHinted(testRoot, "adaptor", AdaptorGenerator(honorNamingAnnotations = true))

    @ParameterizedTest
    @MethodSource("data")
    fun testProxy(testRoot: File) =
        assertHinted(testRoot, "proxy", ProxyGenerator(honorNamingAnnotations = true))

    private fun assertHinted(root: File, prefix: String, generator: BaseGenerator) {
        val xml = introspectionXml.decodeFromString<XmlRootNode>(File(root, "test.xml").readText())
        val actual = generator.transformXmlToFile(xml).sortedBy { it.name }
        val expected = root.listFiles().orEmpty()
            .filter { it.name.startsWith("hinted-$prefix.") && it.name.endsWith(".kt") }
            .sortedBy { it.name }
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (expectedFile, actualFile) ->
            assertEquals(expectedFile.readText(), actualFile.toString())
        }
    }

    companion object {
        /** The fixtures that carry `hinted-` goldens; the rest have no naming annotations. */
        @JvmStatic
        fun data(): Iterable<Array<Any>> = NamingHintTest::class.java.getResource("/MediaPlayer2")
            ?.file
            ?.let { File(it).parentFile.listFiles() }
            .orEmpty()
            .filter { dir -> dir.listFiles().orEmpty().any { it.name.startsWith("hinted-") } }
            .map { arrayOf(it) }
    }
}
