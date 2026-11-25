package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.createPlainMessage
import com.monkopedia.sdbus.deserialize
import com.monkopedia.sdbus.serialize
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Decode-only half of #286: an `ad` written as `List<Double>` must read back as `List<Float>`.
 *
 * Native-only because the JVM backend currently returns the `Double`s unconverted for this read,
 * a separate defect noted on #286 and not fixed there.
 */
class FloatListDecodeTest {

    @Test
    fun doubleList_readsBackAsFloatList() {
        val msg = createPlainMessage()
        msg.serialize(listOf(1.5, -2.5))
        msg.seal()
        assertEquals(listOf(1.5f, -2.5f), msg.deserialize<List<Float>>())
    }
}
