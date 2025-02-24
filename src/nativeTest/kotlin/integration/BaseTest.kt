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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.cinterop.ExperimentalForeignApi

abstract class BaseTest {

    private val fixtures = mutableListOf<TestFixture>()
    private var isInsideTest = false

    fun addFixture(fixture: TestFixture) {
        fixtures.add(fixture)
        if (isInsideTest) {
            println("WARNING: Fixture $fixture added inside of tast method")
        }
    }

    @BeforeTest
    fun onBeforeTest() {
        require(!isInsideTest) {
            "Problem in test lifecycle"
        }
        isInsideTest = true
        fixtures.toList().forEach { it.onBeforeTest() }
    }

    @AfterTest
    fun onAfterTest() {
        runCatching {
            require(isInsideTest) {
                "Problem in test lifecycle"
            }
            isInsideTest = false
            fixtures.toList().forEach {
                it.onAfterTest()
            }
        }
    }
}

interface TestFixture {
    fun onBeforeTest() = Unit
    fun onAfterTest()
}

abstract class BaseTestFixture(test: BaseTest) : TestFixture {
    init {
        @Suppress("LeakingThis")
        test.addFixture(this)
    }
}
