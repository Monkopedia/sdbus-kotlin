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
 * directory named by the single argument, from its `test.xml`. For use after an intentional
 * generator change: `./gradlew :codegen:regenerateGoldens`, then review the resulting `git diff`
 * before committing it.
 *
 * This is deliberately a separate entry point from [GeneratorTest], which only ever compares.
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
        for (fixture in GoldenFixture.entries) {
            val generated = fixture.generate(testRoot)
            fixture.goldenFiles(testRoot).forEach { it.delete() }
            generated.forEachIndexed { index, fileSpec ->
                File(testRoot, "${fixture.prefix}.$index.kt").writeText(fileSpec.toString())
                written++
            }
        }
    }
    println("Regenerated $written golden files across ${fixtures.size} fixtures in $resources")
}
