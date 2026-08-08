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

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Verifies the generators against the golden files checked in beside each fixture's `test.xml`.
 *
 * This test only ever reads the goldens. Rewriting them after an intentional generator change is
 * `./gradlew :codegen:regenerateGoldens` (see `RegenerateGoldens.kt`), so that no run of this suite
 * can produce the output it is about to compare against.
 */
class GeneratorTest {
    @ParameterizedTest
    @MethodSource("data")
    fun testInterface(testRoot: File) {
        assertGoldens(testRoot, GoldenFixture.INTERFACE)
    }

    @ParameterizedTest
    @MethodSource("data")
    fun testAdaptor(testRoot: File) {
        assertGoldens(testRoot, GoldenFixture.ADAPTOR)
    }

    @ParameterizedTest
    @MethodSource("data")
    fun testProxy(testRoot: File) {
        assertGoldens(testRoot, GoldenFixture.PROXY)
    }

    private fun assertGoldens(testRoot: File, fixture: GoldenFixture) {
        val expected = fixture.goldenFiles(testRoot)
        // Without this, a fixture with no goldens of this kind would compare nothing and pass.
        assertTrue(
            expected.isNotEmpty(),
            "No ${fixture.prefix}.*.kt golden files in ${testRoot.name}; " +
                "run :codegen:regenerateGoldens and commit them"
        )
        val actual = fixture.generate(testRoot)
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
    }
}
