package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.SdbusException
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.method
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

/**
 * Cross-backend D-Bus type-matrix round-trip tests (issue #25).
 *
 * Each test stands up a real bus connection with a server object exposing an `Echo` method that
 * returns its argument unchanged, then calls it through a proxy and asserts the returned value
 * equals the value sent. Because this lives in commonTest, the exact same assertions run against
 * BOTH the native sd-bus backend (linuxX64Test) and the JVM wire backend (jvmTest), so any
 * marshalling divergence between the two backends shows up as a test failure on one of them.
 *
 * The values cross a real bus rather than short-circuiting in-process, so wire-serialization bugs
 * (e.g. the empty-`a{sv}` signature corruption #11, or the `ay`->UByte ClassCastException) are
 * exercised against the actual daemon round trip.
 *
 * The matrix covers the basic D-Bus types y b n q i u x t d s o g v, plus arrays (incl. empty),
 * dicts `a{..}` (incl. empty and deeply nested), structs, variants, and nested compositions.
 */
class TypeMatrixRoundTripTest {

    @Serializable
    private data class SimpleStruct(val i: Int, val s: String)

    @Serializable
    private data class WideStruct(
        val y: UByte,
        val b: Boolean,
        val n: Short,
        val q: UShort,
        val i: Int,
        val u: UInt,
        val x: Long,
        val t: ULong,
        val d: Double,
        val s: String,
        val o: ObjectPath,
        val g: Signature,
        val v: Variant
    )

    @Serializable
    private data class NestedStruct(val inner: SimpleStruct, val list: List<Int>)

    private data class FixtureIds(
        val service: ServiceName,
        val path: ObjectPath,
        val iface: InterfaceName
    )

    private fun uniqueFixtureIds(suffix: String): FixtureIds {
        val id = Random.nextInt(100_000, 999_999)
        val base = "com.monkopedia.sdbus.matrix.$suffix$id"
        return FixtureIds(
            service = ServiceName(base),
            path = ObjectPath("/com/monkopedia/sdbus/matrix/$suffix$id"),
            iface = InterfaceName("$base.Interface")
        )
    }

    /**
     * Sends [value] of type [T] to a server's `Echo` method (declared to take and return a [T]),
     * over a real bus, and returns the value that comes back through the proxy. The server handler
     * is the identity function, so the returned value must equal [value] for both backends.
     */
    private inline fun <reified T : Any> roundTrip(suffix: String, value: T): T {
        val ids = uniqueFixtureIds(suffix)
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Echo")) {
                call { input: T -> input }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(
            proxyConnection,
            ids.service,
            ids.path,
            runEventLoopThread = false
        )

        try {
            return proxy.callMethod<T>(ids.iface, MethodName("Echo")) {
                call(value)
            }
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    private inline fun <reified T : Any> assertRoundTrips(suffix: String, value: T) {
        assertEquals(value, roundTrip(suffix, value))
    }

    // --- Basic types ---------------------------------------------------------------------------

    @Test
    fun byte_y_roundTrips() = assertRoundTrips("byte", 42.toUByte())

    @Test
    fun byteExtremes_y_roundTrip() {
        assertRoundTrips("byteMin", UByte.MIN_VALUE)
        assertRoundTrips("byteMax", UByte.MAX_VALUE)
    }

    @Test
    fun boolean_b_roundTrips() {
        assertRoundTrips("boolTrue", true)
        assertRoundTrips("boolFalse", false)
    }

    @Test
    fun int16_n_roundTrips() {
        assertRoundTrips("shortNeg", (-12345).toShort())
        assertRoundTrips("shortMin", Short.MIN_VALUE)
        assertRoundTrips("shortMax", Short.MAX_VALUE)
    }

    @Test
    fun uint16_q_roundTrips() {
        assertRoundTrips("ushort", 54321.toUShort())
        assertRoundTrips("ushortMax", UShort.MAX_VALUE)
    }

    @Test
    fun int32_i_roundTrips() {
        assertRoundTrips("intNeg", -2_000_000_000)
        assertRoundTrips("intMin", Int.MIN_VALUE)
        assertRoundTrips("intMax", Int.MAX_VALUE)
    }

    @Test
    fun uint32_u_roundTrips() {
        assertRoundTrips("uint", 4_000_000_000u)
        assertRoundTrips("uintMax", UInt.MAX_VALUE)
    }

    @Test
    fun int64_x_roundTrips() {
        assertRoundTrips("longNeg", -9_000_000_000_000_000_000L)
        assertRoundTrips("longMin", Long.MIN_VALUE)
        assertRoundTrips("longMax", Long.MAX_VALUE)
    }

    @Test
    fun uint64_t_roundTrips() {
        assertRoundTrips("ulong", 18_000_000_000_000_000_000uL)
        assertRoundTrips("ulongMax", ULong.MAX_VALUE)
    }

    @Test
    fun double_d_roundTrips() {
        assertRoundTrips("double", 3.141592653589793)
        assertRoundTrips("doubleNeg", -2.718281828459045)
    }

    @Test
    fun string_s_roundTrips() {
        assertRoundTrips("string", "Hello, D-Bus éàü")
        assertRoundTrips("stringEmpty", "")
    }

    @Test
    fun objectPath_o_roundTrips() =
        assertRoundTrips("objPath", ObjectPath("/com/monkopedia/sdbus/some/object"))

    @Test
    fun signature_g_roundTrips() = assertRoundTrips("signature", Signature("a{sv}"))

    @Test
    fun variant_v_roundTrips() {
        val sent = Variant(3.14)
        val received = roundTrip("variantDouble", sent)
        assertEquals(sent.get<Double>(), received.get<Double>())

        val sentStr = Variant("inside-variant")
        val receivedStr = roundTrip("variantString", sentStr)
        assertEquals(sentStr.get<String>(), receivedStr.get<String>())
    }

    // --- Arrays --------------------------------------------------------------------------------

    @Test
    fun arrayOfInts_ai_roundTrips() = assertRoundTrips("arrInt", listOf(1, 2, 3, -4, Int.MAX_VALUE))

    @Test
    fun arrayOfStrings_as_roundTrips() = assertRoundTrips("arrStr", listOf("a", "b", "c"))

    @Test
    fun arrayOfBytes_ay_roundTrips() =
        assertRoundTrips("arrByte", listOf<UByte>(0u, 1u, 127u, 255u))

    @Test
    fun arrayOfDoubles_ad_roundTrips() = assertRoundTrips("arrDouble", listOf(1.5, -2.5, 0.0))

    @Test
    fun emptyArrayOfInts_ai_roundTrips() = assertRoundTrips("arrIntEmpty", emptyList<Int>())

    @Test
    fun emptyArrayOfStrings_as_roundTrips() = assertRoundTrips("arrStrEmpty", emptyList<String>())

    @Test
    fun arrayOfArrays_aai_roundTrips() =
        assertRoundTrips("arrArr", listOf(listOf(1, 2), emptyList(), listOf(3)))

    // --- Dicts ---------------------------------------------------------------------------------

    @Test
    fun dictStringString_ass_roundTrips() =
        assertRoundTrips("dictSS", mapOf("one" to "1", "two" to "2"))

    @Test
    fun dictIntLong_aix_roundTrips() = assertRoundTrips("dictIX", mapOf(1 to 10L, 2 to 20L))

    @Test
    fun dictStringVariant_asv_roundTrips() {
        val sent = mapOf("count" to Variant(7), "name" to Variant("widget"))
        val received = roundTrip("dictSV", sent)
        assertEquals(sent.keys, received.keys)
        assertEquals(7, received.getValue("count").get<Int>())
        assertEquals("widget", received.getValue("name").get<String>())
    }

    @Test
    fun emptyDictStringVariant_asv_roundTrips() =
        assertRoundTrips("dictSVEmpty", emptyMap<String, Variant>())

    @Test
    fun emptyDictStringString_ass_roundTrips() =
        assertRoundTrips("dictSSEmpty", emptyMap<String, String>())

    @Test
    fun deeplyNestedDict_aoa_sa_sv_roundTrips() {
        // a{oa{sa{sv}}} -- the nested-dict-of-variant shape called out in the issue.
        val sent: Map<ObjectPath, Map<String, Map<String, Variant>>> = mapOf(
            ObjectPath("/dev/0") to mapOf(
                "org.example.Iface" to mapOf(
                    "Enabled" to Variant(true),
                    "Level" to Variant(5)
                )
            ),
            ObjectPath("/dev/1") to mapOf(
                "org.example.Other" to emptyMap()
            )
        )
        val received = roundTrip("dictNested", sent)
        assertEquals(sent.keys, received.keys)
        assertEquals(
            true,
            received
                .getValue(ObjectPath("/dev/0"))
                .getValue("org.example.Iface")
                .getValue("Enabled")
                .get<Boolean>()
        )
        assertEquals(
            5,
            received
                .getValue(ObjectPath("/dev/0"))
                .getValue("org.example.Iface")
                .getValue("Level")
                .get<Int>()
        )
        assertTrue(
            received
                .getValue(ObjectPath("/dev/1"))
                .getValue("org.example.Other")
                .isEmpty()
        )
    }

    // --- Structs -------------------------------------------------------------------------------

    @Test
    fun struct_is_roundTrips() = assertRoundTrips("structIS", SimpleStruct(99, "ninety-nine"))

    @Test
    fun wideStruct_allBasicTypes_roundTrips() {
        val sent = WideStruct(
            y = 7u,
            b = true,
            n = -100,
            q = 60_000u,
            i = -1_000_000,
            u = 3_000_000_000u,
            x = -5_000_000_000L,
            t = 10_000_000_000uL,
            d = 1.25,
            s = "wide",
            o = ObjectPath("/wide/struct"),
            g = Signature("(qdsv)"),
            v = Variant(123)
        )
        val received = roundTrip("structWide", sent)
        // Variant has no value-equality, so compare each field individually and the variant
        // payload by its contained value.
        assertEquals(sent.y, received.y)
        assertEquals(sent.b, received.b)
        assertEquals(sent.n, received.n)
        assertEquals(sent.q, received.q)
        assertEquals(sent.i, received.i)
        assertEquals(sent.u, received.u)
        assertEquals(sent.x, received.x)
        assertEquals(sent.t, received.t)
        assertEquals(sent.d, received.d)
        assertEquals(sent.s, received.s)
        assertEquals(sent.o, received.o)
        assertEquals(sent.g, received.g)
        assertEquals(sent.v.get<Int>(), received.v.get<Int>())
    }

    @Test
    fun nestedStruct_roundTrips() =
        assertRoundTrips("structNested", NestedStruct(SimpleStruct(1, "a"), listOf(7, 8, 9)))

    // --- Nested compositions -------------------------------------------------------------------

    @Test
    fun arrayOfStructs_a_is_roundTrips() = assertRoundTrips(
        "arrStruct",
        listOf(SimpleStruct(1, "one"), SimpleStruct(2, "two"))
    )

    @Test
    fun dictOfArrays_aias_roundTrips() = assertRoundTrips(
        "dictArr",
        mapOf(1 to listOf("a", "b"), 2 to emptyList())
    )

    @Test
    fun variantContainingArray_roundTrips() {
        val sent = Variant(listOf(1, 2, 3))
        val received = roundTrip("variantArr", sent)
        assertEquals(listOf(1, 2, 3), received.get<List<Int>>())
    }

    @Test
    fun variantContainingDict_roundTrips() {
        val sent = Variant(mapOf("k" to "v"))
        val received = roundTrip("variantDict", sent)
        assertEquals(mapOf("k" to "v"), received.get<Map<String, String>>())
    }

    // --- Signature-mismatch strictness (issue #56) -----------------------------------------------

    @Serializable
    private data class IntPair(val first: Int, val second: Int)

    // Restores the failure-path sub-case dropped in PR #54: deserializing a reply whose actual
    // signature is a single Int (i) into an expected struct type (ii) must be REJECTED on both
    // backends rather than silently returning a value. The native backend fails entering the
    // struct container with -ENXIO (System.Error.ENXIO); the JVM deserializer enforces the same
    // contract with the same error name.
    @Test
    fun mismatchedReply_intIntoStruct_throwsOnBothBackends() {
        val ids = uniqueFixtureIds("mismatch")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        // The server's Echo is declared and replies with a single Int (signature "i") ...
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Echo")) {
                call { input: Int -> input }
            }
        }
        serverConnection.startEventLoop()
        val proxy = createProxy(
            proxyConnection,
            ids.service,
            ids.path,
            runEventLoopThread = false
        )

        try {
            // ... while the client deserializes the reply as a struct (signature "(ii)").
            val thrown = assertFailsWith<SdbusException> {
                proxy.callMethod<IntPair>(ids.iface, MethodName("Echo")) {
                    call(7)
                }
            }
            assertEquals("System.Error.ENXIO", thrown.name)
        } finally {
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }
}
