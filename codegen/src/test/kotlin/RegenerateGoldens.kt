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

/**
 * Rewrites the checked-in golden files of every fixture directory under the test-resources
 * directory named by the single argument, from its `test.xml` — the default output for every
 * fixture, plus the `hinted-` output for those that pin the naming-annotation side of the flag too.
 * For use after an intentional generator change: `./gradlew :codegen:regenerateGoldens`, then
 * review the resulting `git diff` before committing it.
 *
 * This is deliberately a separate entry point from [GeneratorTest] and [NamingHintTest], which only
 * ever compare.
 * Regenerating and verifying are then different operations that cannot be mistaken for each other —
 * no run of the test suite can write the answer it is about to check.
 */
fun main(args: Array<String>) {
    require(args.size == 1) { "usage: RegenerateGoldens <test-resources-dir>" }
    val resources = File(args[0])
    val fixtures = resources.listFiles().orEmpty()
        .filter { File(it, "test.xml").isFile }
        .sortedBy { it.name }
    require(fixtures.isNotEmpty()) { "No fixture directories with a test.xml found in $resources" }

    var written = 0
    for (testRoot in fixtures) {
        // A fixture only carries `hinted-` goldens if its XML has naming annotations to honor;
        // writing them everywhere would invent goldens no test asserts.
        val sides =
            if (GoldenFixture.hasHintedGoldens(testRoot)) listOf(false, true) else listOf(false)
        for (fixture in GoldenFixture.entries) {
            for (hinted in sides) {
                val generated = fixture.generate(testRoot, hinted)
                fixture.goldenFiles(testRoot, hinted).forEach { it.delete() }
                generated.forEachIndexed { index, fileSpec ->
                    File(testRoot, "${fixture.prefix(hinted)}.$index.kt")
                        .writeText(fileSpec.toString())
                    written++
                }
            }
        }
    }
    println("Regenerated $written golden files across ${fixtures.size} fixtures in $resources")
}
