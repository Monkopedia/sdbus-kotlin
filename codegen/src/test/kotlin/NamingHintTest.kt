package com.monkopedia.sdbus

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The opt-in half of [GeneratorTest]. It runs over the same fixture directories with naming
 * annotations honored and compares against the `hinted-` goldens, so a fixture that carries both
 * `interface.0.kt` and `hinted-interface.0.kt` pins both sides of the flag from one XML.
 *
 * Like [GeneratorTest] it only ever reads the goldens; `./gradlew :codegen:regenerateGoldens`
 * rewrites both sides.
 */
class NamingHintTest {

    @ParameterizedTest
    @MethodSource("data")
    fun testInterface(testRoot: File) = assertHinted(testRoot, GoldenFixture.INTERFACE)

    @ParameterizedTest
    @MethodSource("data")
    fun testAdaptor(testRoot: File) = assertHinted(testRoot, GoldenFixture.ADAPTOR)

    @ParameterizedTest
    @MethodSource("data")
    fun testProxy(testRoot: File) = assertHinted(testRoot, GoldenFixture.PROXY)

    private fun assertHinted(testRoot: File, fixture: GoldenFixture) {
        val expected = fixture.goldenFiles(testRoot, hinted = true)
        // Without this, a fixture with no goldens of this kind would compare nothing and pass.
        assertTrue(
            expected.isNotEmpty(),
            "No ${fixture.prefix(hinted = true)}.*.kt golden files in ${testRoot.name}; " +
                "run :codegen:regenerateGoldens and commit them"
        )
        val actual = fixture.generate(testRoot, hinted = true)
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
            .filter { GoldenFixture.hasHintedGoldens(it) }
            .map { arrayOf(it) }
    }
}
