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
