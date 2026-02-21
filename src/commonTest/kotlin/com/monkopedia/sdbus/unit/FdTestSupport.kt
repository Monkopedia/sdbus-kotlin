package com.monkopedia.sdbus.unit

internal expect object FdTestSupport {
    val supportsFdDuplicationSemantics: Boolean
    fun createPipePair(): Pair<Int, Int>
    fun closeFd(fd: Int): Int
}
