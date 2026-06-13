/**
 *
 * (C) 2024 - 2026 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertiesProxy
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.getProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable

/**
 * Independent-peer D-Bus type-matrix and marshalling edge-case tests (issue #35) — the
 * dbusmock counterpart of the own-server [TypeMatrixRoundTripTest-style] coverage.
 *
 * Each test launches a generic python-dbusmock object (a foreign Python/GDBus encoder/decoder),
 * scripts identity `Echo` methods on it for various D-Bus signatures via the
 * `org.freedesktop.DBus.Mock` control interface, and round-trips values through it. Because
 * the peer is an *independent* D-Bus implementation, this catches *symmetric* serializer bugs
 * (encode-wrong + decode-wrong in matching ways) that own-server round-trips structurally
 * cannot: a value only comes back equal if our wire encoding is correct enough for the foreign
 * stack to decode it and re-encode an equivalent reply.
 *
 * Lives in commonTest so the exact same assertions run against BOTH the native sd-bus backend
 * (`linuxX64Test`) and the JVM dbus-java backend (`jvmTest`).
 *
 * Skips cleanly when python3 / python3-dbusmock is not installed (e.g. the ARM CI job). See
 * [DbusmockHarness] for installation instructions.
 */
class DbusmockTypeMatrixTest {

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

    // --- Basic types -----------------------------------------------------------------------

    @Test
    fun integralTypes_roundTripThroughForeignPeer() = withDbusmockPeer("MatrixIntegral") {
        addEcho("EchoY", "y")
        assertEchoes("EchoY", UByte.MIN_VALUE)
        assertEchoes("EchoY", 42.toUByte())
        assertEchoes("EchoY", UByte.MAX_VALUE)

        addEcho("EchoB", "b")
        assertEchoes("EchoB", true)
        assertEchoes("EchoB", false)

        addEcho("EchoN", "n")
        assertEchoes("EchoN", (-12345).toShort())
        assertEchoes("EchoN", Short.MIN_VALUE)
        assertEchoes("EchoN", Short.MAX_VALUE)

        addEcho("EchoQ", "q")
        assertEchoes("EchoQ", UShort.MIN_VALUE)
        assertEchoes("EchoQ", 54321.toUShort())
        assertEchoes("EchoQ", UShort.MAX_VALUE)

        addEcho("EchoI", "i")
        assertEchoes("EchoI", -2_000_000_000)
        assertEchoes("EchoI", Int.MIN_VALUE)
        assertEchoes("EchoI", Int.MAX_VALUE)

        addEcho("EchoU", "u")
        assertEchoes("EchoU", 4_000_000_000u)
        assertEchoes("EchoU", UInt.MIN_VALUE)
        assertEchoes("EchoU", UInt.MAX_VALUE)

        addEcho("EchoX", "x")
        assertEchoes("EchoX", -9_000_000_000_000_000_000L)
        assertEchoes("EchoX", Long.MIN_VALUE)
        assertEchoes("EchoX", Long.MAX_VALUE)

        addEcho("EchoT", "t")
        assertEchoes("EchoT", 18_000_000_000_000_000_000uL)
        assertEchoes("EchoT", ULong.MIN_VALUE)
        // ULong.MAX_VALUE crosses the signed-Long boundary; a backend that round-trips
        // through a signed 64-bit type wrongly would corrupt this against a foreign peer.
        assertEchoes("EchoT", ULong.MAX_VALUE)
    }

    @Test
    fun floatingPointAndStringLikeTypes_roundTripThroughForeignPeer() =
        withDbusmockPeer("MatrixFp") {
            addEcho("EchoD", "d")
            assertEchoes("EchoD", 3.141592653589793)
            assertEchoes("EchoD", -2.718281828459045)
            assertEchoes("EchoD", 1.0E-300)
            assertEchoes("EchoD", 1.0E300)
            assertEchoes("EchoD", Double.POSITIVE_INFINITY)
            assertEchoes("EchoD", Double.NEGATIVE_INFINITY)
            // NaN and -0.0 must survive bit-exact; kotlin boxed equality distinguishes both.
            assertEchoes("EchoD", Double.NaN)
            assertEchoes("EchoD", -0.0)

            addEcho("EchoS", "s")
            assertEchoes("EchoS", "")
            assertEchoes("EchoS", "Hello, D-Bus éàü 你好 🚀")
            assertEchoes("EchoS", "line1\nline2\ttab \"quoted\" back\\slash")

            addEcho("EchoO", "o")
            assertEchoes("EchoO", ObjectPath("/com/monkopedia/sdbus/some/object"))
            assertEchoes("EchoO", ObjectPath("/"))

            addEcho("EchoG", "g")
            assertEchoes("EchoG", Signature("a{sv}"))
            assertEchoes("EchoG", Signature("(ybnqiuxtdsogv)"))
            assertEchoes("EchoG", Signature(""))
        }

    @Test
    fun variants_roundTripThroughForeignPeer() = withDbusmockPeer("MatrixVariant") {
        addEcho("EchoV", "v")
        assertEquals(7, echo("EchoV", Variant(7)).get<Int>())
        assertEquals("inside-variant", echo("EchoV", Variant("inside-variant")).get<String>())
        assertEquals(2.5, echo("EchoV", Variant(2.5)).get<Double>())
        assertEquals(ULong.MAX_VALUE, echo("EchoV", Variant(ULong.MAX_VALUE)).get<ULong>())
        assertEquals(
            listOf(1, 2, 3),
            echo("EchoV", Variant(listOf(1, 2, 3))).get<List<Int>>()
        )
        assertEquals(
            mapOf("k" to "v"),
            echo("EchoV", Variant(mapOf("k" to "v"))).get<Map<String, String>>()
        )
        assertEquals(
            ObjectPath("/in/variant"),
            echo("EchoV", Variant(ObjectPath("/in/variant"))).get<ObjectPath>()
        )

        if (peerStructMarshallingSupported) {
            // Receive direction for a struct inside a variant (constructed by the foreign peer).
            addMethod(
                "MakeVStruct",
                "",
                "v",
                "import dbus; ret = dbus.Struct((5, 'five'), signature='is', variant_level=1)"
            )
            val vStruct = proxy.callMethod<Variant>(iface, MethodName("MakeVStruct")) {}
            assertEquals(SimpleStruct(5, "five"), vStruct.get<SimpleStruct>())

            assertEquals(
                SimpleStruct(5, "five"),
                echo("EchoV", Variant(SimpleStruct(5, "five"))).get<SimpleStruct>()
            )
        } else {
            // KNOWN JVM BACKEND BUG (found by this suite; ticketed separately from issue #35;
            // see peerStructMarshallingSupported KDoc for full details).
            println(
                "[DbusmockTypeMatrixTest] SKIP variant-wrapped struct sub-cases: " +
                    "known JVM backend struct marshalling gap against remote peers."
            )
        }
    }

    // --- Arrays ----------------------------------------------------------------------------

    @Test
    fun arrays_roundTripThroughForeignPeer() = withDbusmockPeer("MatrixArray") {
        addEcho("EchoAi", "ai")
        assertEchoes("EchoAi", listOf(1, 2, 3, -4, Int.MAX_VALUE, Int.MIN_VALUE))
        assertEchoes("EchoAi", emptyList<Int>())

        addEcho("EchoAs", "as")
        assertEchoes("EchoAs", listOf("a", "", "c with spaces", "déjà-vu"))
        assertEchoes("EchoAs", emptyList<String>())

        addEcho("EchoAy", "ay")
        assertEchoes("EchoAy", listOf<UByte>(0u, 1u, 127u, 128u, 255u))
        assertEchoes("EchoAy", emptyList<UByte>())

        addEcho("EchoAd", "ad")
        assertEchoes("EchoAd", listOf(1.5, -2.5, 0.0, 1.0E300))

        addEcho("EchoAt", "at")
        assertEchoes("EchoAt", listOf(0uL, 1uL, ULong.MAX_VALUE))

        addEcho("EchoAb", "ab")
        assertEchoes("EchoAb", listOf(true, false, true))

        addEcho("EchoAo", "ao")
        assertEchoes("EchoAo", listOf(ObjectPath("/a"), ObjectPath("/a/b")))

        addEcho("EchoAai", "aai")
        assertEchoes("EchoAai", listOf(listOf(1, 2), emptyList(), listOf(3)))

        addEcho("EchoAv", "av")
        val variants = echo("EchoAv", listOf(Variant(13), Variant("two"), Variant(listOf(9))))
        assertEquals(3, variants.size)
        assertEquals(13, variants[0].get<Int>())
        assertEquals("two", variants[1].get<String>())
        assertEquals(listOf(9), variants[2].get<List<Int>>())
        assertTrue(echo("EchoAv", emptyList<Variant>()).isEmpty())
    }

    // --- Dictionaries ----------------------------------------------------------------------

    @Test
    fun dictionaries_roundTripThroughForeignPeer() = withDbusmockPeer("MatrixDict") {
        addEcho("EchoDss", "a{ss}")
        assertEchoes("EchoDss", mapOf("one" to "1", "two" to "2", "" to "empty-key"))
        assertEchoes("EchoDss", emptyMap<String, String>())

        addEcho("EchoDix", "a{ix}")
        assertEchoes("EchoDix", mapOf(1 to 10L, -2 to Long.MIN_VALUE, 3 to Long.MAX_VALUE))

        addEcho("EchoDyu", "a{yu}")
        assertEchoes(
            "EchoDyu",
            mapOf<UByte, UInt>(UByte.MIN_VALUE to UInt.MAX_VALUE, UByte.MAX_VALUE to 0u)
        )

        addEcho("EchoDsv", "a{sv}")
        val sent = mapOf("count" to Variant(7), "name" to Variant("widget"))
        val received = echo("EchoDsv", sent)
        assertEquals(sent.keys, received.keys)
        assertEquals(7, received.getValue("count").get<Int>())
        assertEquals("widget", received.getValue("name").get<String>())
        assertTrue(echo("EchoDsv", emptyMap<String, Variant>()).isEmpty())

        addEcho("EchoDias", "a{ias}")
        assertEchoes("EchoDias", mapOf(1 to listOf("a", "b"), 2 to emptyList()))

        // a{oa{sa{sv}}}: the GetManagedObjects-style nested dict shape.
        addEcho("EchoNested", "a{oa{sa{sv}}}")
        val nested: Map<ObjectPath, Map<String, Map<String, Variant>>> = mapOf(
            ObjectPath("/dev/0") to mapOf(
                "org.example.Iface" to mapOf(
                    "Enabled" to Variant(true),
                    "Level" to Variant(5)
                )
            ),
            ObjectPath("/dev/1") to mapOf("org.example.Other" to emptyMap())
        )
        val nestedBack = echo("EchoNested", nested)
        assertEquals(nested.keys, nestedBack.keys)
        val dev0 = nestedBack.getValue(ObjectPath("/dev/0")).getValue("org.example.Iface")
        assertEquals(true, dev0.getValue("Enabled").get<Boolean>())
        assertEquals(5, dev0.getValue("Level").get<Int>())
        assertTrue(
            nestedBack.getValue(ObjectPath("/dev/1")).getValue("org.example.Other").isEmpty()
        )
    }

    // --- Structs ---------------------------------------------------------------------------

    @Test
    fun structs_receivedFromForeignPeer_deserializeCorrectly() =
        withDbusmockPeer("MatrixStructIn") {
            if (!peerStructMarshallingSupported) {
                // KNOWN JVM BACKEND BUG (see peerStructMarshallingSupported KDoc): struct replies
                // from a real remote peer fail signature validation ("expected=(is) actual=ai").
                println(
                    "[DbusmockTypeMatrixTest] SKIP struct receive sub-cases: known JVM backend " +
                        "struct marshalling gap against remote peers."
                )
                return@withDbusmockPeer
            }

            // Receive-direction struct coverage: the foreign peer constructs the struct values,
            // our client only deserializes them.
            addMethod("MakeStruct", "", "(is)", "ret = (99, 'ninety-nine')")
            assertEquals(
                SimpleStruct(99, "ninety-nine"),
                proxy.callMethod(iface, MethodName("MakeStruct")) {}
            )

            addMethod("MakeArrStruct", "", "a(is)", "ret = [(1, 'one'), (2, 'two')]")
            assertEquals(
                listOf(SimpleStruct(1, "one"), SimpleStruct(2, "two")),
                proxy.callMethod(iface, MethodName("MakeArrStruct")) {}
            )

            addMethod("MakeEmptyArrStruct", "", "a(is)", "ret = []")
            assertEquals(
                emptyList(),
                proxy.callMethod<List<SimpleStruct>>(iface, MethodName("MakeEmptyArrStruct")) {}
            )

            addMethod("MakeNested", "", "((is)ai)", "ret = ((1, 'a'), [7, 8, 9])")
            assertEquals(
                NestedStruct(SimpleStruct(1, "a"), listOf(7, 8, 9)),
                proxy.callMethod(iface, MethodName("MakeNested")) {}
            )
        }

    @Test
    fun structs_roundTripThroughForeignPeer() = withDbusmockPeer("MatrixStruct") {
        if (!peerStructMarshallingSupported) {
            // KNOWN JVM BACKEND BUG (see peerStructMarshallingSupported KDoc).
            println(
                "[DbusmockTypeMatrixTest] SKIP struct round-trip sub-cases: known JVM backend " +
                    "struct marshalling gap against remote peers."
            )
            return@withDbusmockPeer
        }

        addEcho("EchoStruct", "(is)")
        assertEchoes("EchoStruct", SimpleStruct(99, "ninety-nine"))

        addEcho("EchoWide", "(ybnqiuxtdsogv)")
        val sentWide = WideStruct(
            y = 7u,
            b = true,
            n = -100,
            q = 60_000u,
            i = -1_000_000,
            u = 3_000_000_000u,
            x = -5_000_000_000L,
            t = ULong.MAX_VALUE,
            d = 1.25,
            s = "wide",
            o = ObjectPath("/wide/struct"),
            g = Signature("(qdsv)"),
            v = Variant(123)
        )
        val wide = echo("EchoWide", sentWide)
        // Variant has no value-equality; compare fields and the variant payload separately.
        assertEquals(sentWide.y, wide.y)
        assertEquals(sentWide.b, wide.b)
        assertEquals(sentWide.n, wide.n)
        assertEquals(sentWide.q, wide.q)
        assertEquals(sentWide.i, wide.i)
        assertEquals(sentWide.u, wide.u)
        assertEquals(sentWide.x, wide.x)
        assertEquals(sentWide.t, wide.t)
        assertEquals(sentWide.d, wide.d)
        assertEquals(sentWide.s, wide.s)
        assertEquals(sentWide.o, wide.o)
        assertEquals(sentWide.g, wide.g)
        assertEquals(sentWide.v.get<Int>(), wide.v.get<Int>())

        addEcho("EchoNestedStruct", "((is)ai)")
        assertEchoes("EchoNestedStruct", NestedStruct(SimpleStruct(1, "a"), listOf(7, 8, 9)))
        assertEchoes("EchoNestedStruct", NestedStruct(SimpleStruct(0, ""), emptyList()))

        addEcho("EchoArrStruct", "a(is)")
        assertEchoes("EchoArrStruct", listOf(SimpleStruct(1, "one"), SimpleStruct(2, "two")))
        assertEchoes("EchoArrStruct", emptyList<SimpleStruct>())
    }

    // --- Large payloads --------------------------------------------------------------------

    @Test
    fun largePayloads_roundTripThroughForeignPeer() = withDbusmockPeer("MatrixLarge") {
        addEcho("EchoAy", "ay")
        val bigBytes = List(128 * 1024) { (it % 256).toUByte() }
        assertEquals(bigBytes, echo("EchoAy", bigBytes), "128KiB byte array corrupted")

        addEcho("EchoAs", "as")
        val manyStrings = List(2000) { "string-$it-${"x".repeat(it % 32)}" }
        assertEquals(manyStrings, echo("EchoAs", manyStrings), "2000-element string array")

        addEcho("EchoS", "s")
        val bigString = buildString { repeat(64 * 1024) { append(('a' + (it % 26))) } }
        assertEquals(bigString, echo("EchoS", bigString), "64KiB string corrupted")
    }

    // --- Multi-arg / no-arg / no-return methods ---------------------------------------------

    @Test
    fun argumentShapes_roundTripThroughForeignPeer() = withDbusmockPeer("MatrixArgs") {
        // Multiple heterogeneous in-args, single out.
        addMethod("Concat", "iis", "s", "ret = '%d|%d|%s' % (args[0], args[1], args[2])")
        val concat = proxy.callMethod<String>(iface, MethodName("Concat")) {
            call(-7, 42, "tail")
        }
        assertEquals("-7|42|tail", concat)

        // Mixed unsigned widths.
        addMethod("Sum", "tu", "t", "ret = args[0] + args[1]")
        val sum = proxy.callMethod<ULong>(iface, MethodName("Sum")) {
            call(9_000_000_000_000_000_000uL, 4_000_000_000u)
        }
        assertEquals(9_000_000_004_000_000_000uL, sum)

        // No in-args.
        addMethod("NoArgs", "", "i", "ret = 1234")
        val noArgs = proxy.callMethod<Int>(iface, MethodName("NoArgs")) {}
        assertEquals(1234, noArgs)

        // No return value.
        addMethod("NoReturn", "s", "", "")
        proxy.callMethod<Unit>(iface, MethodName("NoReturn")) {
            call("ignored")
        }

        // Container in, container out (different types in/out).
        addMethod("Keys", "a{ss}", "as", "ret = sorted(args[0].keys())")
        val keys = proxy.callMethod<List<String>>(iface, MethodName("Keys")) {
            call(mapOf("b" to "2", "a" to "1"))
        }
        assertEquals(listOf("a", "b"), keys)
    }

    // --- Suspend/async call path ------------------------------------------------------------

    @Test
    fun asyncCalls_roundTripThroughForeignPeer() = withDbusmockPeer("MatrixAsync") {
        addEcho("EchoAi", "ai")
        addEcho("EchoDsv", "a{sv}")
        val ints = proxy.callMethodAsync<List<Int>>(iface, MethodName("EchoAi")) {
            call(listOf(5, 6, 7))
        }
        assertEquals(listOf(5, 6, 7), ints)

        val dict = proxy.callMethodAsync<Map<String, Variant>>(iface, MethodName("EchoDsv")) {
            call(mapOf("answer" to Variant(42)))
        }
        assertEquals(setOf("answer"), dict.keys)
        assertEquals(42, dict.getValue("answer").get<Int>())
    }

    // --- Properties with complex types -------------------------------------------------------

    @Test
    fun complexProperties_readThroughForeignPeer() = withDbusmockPeer("MatrixProps") {
        addProperty("Dict", Variant(mapOf("k1" to "v1", "k2" to "v2")))
        addProperty("Ints", Variant(listOf(3, 1, 4, 1, 5)))
        addProperty("Big", Variant(ULong.MAX_VALUE))

        assertEquals(
            mapOf("k1" to "v1", "k2" to "v2"),
            proxy.getProperty<Map<String, String>>(iface, PropertyName("Dict"))
        )
        assertEquals(
            listOf(3, 1, 4, 1, 5),
            proxy.getProperty<List<Int>>(iface, PropertyName("Ints"))
        )
        assertEquals(
            ULong.MAX_VALUE,
            proxy.getProperty<ULong>(iface, PropertyName("Big"))
        )

        // GetAll through the canonical org.freedesktop.DBus.Properties interface.
        val all = proxy.callMethod<Map<String, Variant>>(
            PropertiesProxy.INTERFACE_NAME,
            MethodName("GetAll")
        ) {
            call(iface.value)
        }
        assertEquals(setOf("Dict", "Ints", "Big"), all.keys)
        assertEquals(listOf(3, 1, 4, 1, 5), all.getValue("Ints").get<List<Int>>())
    }

    // --- Unix fd passing ----------------------------------------------------------------------

    @Test
    fun unixFd_passesThroughForeignPeer_whereSupported() = withDbusmockPeer("MatrixFd") {
        val pipe = createTestPipe()
        if (pipe == null) {
            println(
                "[DbusmockTypeMatrixTest] SKIP unix-fd case: raw file descriptors are not " +
                    "accessible from this platform's test environment."
            )
            return@withDbusmockPeer
        }
        val (readFd, writeFd) = pipe
        addEcho("EchoH", "h")
        val sent = UnixFd(readFd) // Duplicates; we retain ownership of readFd.
        try {
            val received = echo("EchoH", sent)
            try {
                // The echoed fd must be a live duplicate of the pipe's read end: bytes written
                // to the original write end must be readable from it.
                val payload = "fd-through-dbusmock"
                assertTrue(
                    writeToFd(writeFd, payload.encodeToByteArray()),
                    "writing into the original pipe failed"
                )
                assertEquals(payload, readFromFd(received.fd, 64)?.decodeToString())
            } finally {
                received.release()
            }
        } finally {
            sent.release()
            closeTestFd(readFd)
            closeTestFd(writeFd)
        }
    }

    // --- Fixture ------------------------------------------------------------------------------
    // The peer lifecycle ([withDbusmockPeer] / [DbusmockPeer]) is shared with the other
    // dbusmock suites; see DbusmockPeerFixture.kt.

    /** Calls the identity method [name] on the peer with [value] and returns the reply. */
    private inline fun <reified T : Any> DbusmockPeer.echo(name: String, value: T): T =
        proxy.callMethod(iface, MethodName(name)) {
            call(value)
        }

    private inline fun <reified T : Any> DbusmockPeer.assertEchoes(name: String, value: T) {
        assertEquals(
            value,
            echo(name, value),
            "$name: value did not round-trip through the dbusmock peer"
        )
    }
}

/**
 * Whether this backend can marshal custom @Serializable struct values to/from a real remote
 * (out-of-process) peer.
 *
 * `true` on both backends. The native sd-bus backend always supported it; on the JVM the owned
 * wire backend decomposes structs into wire-shaped values carrying their exact signature
 * ([com.monkopedia.sdbus.Message.JvmStructPayload]) on the way to and from the marshaller, so a
 * struct argument or reply round-trips against a remote peer.
 *
 * Found by this suite (issue #35) against the old dbus-java backend, which failed to marshal
 * structs to/from remote destinations; fixed under issue #71 and the owned wire marshaller
 * (epic #93). Both actuals are now `true`.
 */
internal expect val peerStructMarshallingSupported: Boolean

/**
 * Creates a unidirectional pipe and returns `(readFd, writeFd)`, or `null` when raw file
 * descriptors are not accessible on this platform (the unix-fd case is then skipped).
 */
internal expect fun createTestPipe(): Pair<Int, Int>?

/** Writes [data] to [fd], returning true when all bytes were written. */
internal expect fun writeToFd(fd: Int, data: ByteArray): Boolean

/** Reads up to [maxBytes] from [fd], or `null` on failure. */
internal expect fun readFromFd(fd: Int, maxBytes: Int): ByteArray?

/** Closes [fd], ignoring errors. */
internal expect fun closeTestFd(fd: Int)
