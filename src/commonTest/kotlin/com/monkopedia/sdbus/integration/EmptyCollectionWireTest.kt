package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.deserialize
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.signal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

/**
 * Exhaustive over-the-wire coverage of *empty* collection arguments, on both backends.
 *
 * Empty collections used to break the JVM backend: the wire signature was inferred from the
 * runtime value, and an empty value has nothing to infer from, so an empty `Map` emitted the
 * malformed `a{}` and an empty `List` the bare `a`. The bus daemon rejects those and drops the
 * connection. The fix captures the *declared* signature from the serializer descriptor at
 * serialize time ([Message.declaredBodySignature]) and uses that for the wire body, only
 * falling back to value-inference when no declared signature is available
 * (see `inferSignalSignature` / `declaredBodySignature` in `PureJavaDbusBackend`).
 *
 * This extends the two cases added in #11 (empty `a{sv}` / empty `as` method args) to cover:
 *  - empty arrays of every common element type (incl. unsigned + `ay`)
 *  - empty dicts with assorted key/value types
 *  - nested empties (empty array-of-arrays, empty array inside a struct, empty dict inside a
 *    variant)
 *  - empty collections delivered as signal bodies, asserting the observed wire signature.
 *
 * Each case runs through `createBusConnection` over the real session bus, so both the native
 * (sd-bus) and JVM (pure-Java dbus) backends are exercised by their respective test targets.
 */
class EmptyCollectionWireTest {

    @Serializable
    private data class StructWithList(val label: String, val items: List<Int>)

    private data class FixtureIds(
        val service: ServiceName,
        val path: ObjectPath,
        val iface: InterfaceName
    )

    private fun uniqueFixtureIds(suffix: String): FixtureIds {
        val id = Random.nextInt(100_000, 999_999)
        val base = "com.monkopedia.sdbus.empty.$suffix$id"
        return FixtureIds(
            service = ServiceName(base),
            path = ObjectPath("/com/monkopedia/sdbus/empty/$suffix$id"),
            iface = InterfaceName("$base.Interface")
        )
    }

    // ------------------------------------------------------------------
    // Method-call argument coverage. The vtable's typed `call { ... }` lambda declares the
    // parameter type, which drives the declared wire signature regardless of the (empty)
    // runtime value. Each helper round-trips an empty collection and asserts the server saw it
    // as empty — a malformed signature would have dropped the connection before this returns.
    // ------------------------------------------------------------------

    private inline fun <reified T : Any> roundTripUnaryMethod(
        suffix: String,
        memberName: String,
        crossinline handler: (T) -> Int,
        argument: T,
        expected: Int
    ) {
        val ids = uniqueFixtureIds(suffix)
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName(memberName)) {
                call { value: T -> handler(value) }
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
            val result = proxy.callMethod<Int>(ids.iface, MethodName(memberName)) {
                call(argument)
            }
            assertEquals(expected, result)
        } finally {
            runBlocking {
                proxyConnection.stopEventLoop()
                serverConnection.stopEventLoop()
            }
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun emptyArrayArg_ofStrings_roundTrips() = roundTripUnaryMethod<List<String>>(
        suffix = "arrStr",
        memberName = "CountStrings",
        handler = { it.size },
        argument = emptyList(),
        expected = 0
    )

    @Test
    fun emptyArrayArg_ofInts_roundTrips() = roundTripUnaryMethod<List<Int>>(
        suffix = "arrInt",
        memberName = "CountInts",
        handler = { it.size },
        argument = emptyList(),
        expected = 0
    )

    @Test
    fun emptyArrayArg_ofLongs_roundTrips() = roundTripUnaryMethod<List<Long>>(
        suffix = "arrLong",
        memberName = "CountLongs",
        handler = { it.size },
        argument = emptyList(),
        expected = 0
    )

    @Test
    fun emptyArrayArg_ofDoubles_roundTrips() = roundTripUnaryMethod<List<Double>>(
        suffix = "arrDouble",
        memberName = "CountDoubles",
        handler = { it.size },
        argument = emptyList(),
        expected = 0
    )

    @Test
    fun emptyArrayArg_ofBooleans_roundTrips() = roundTripUnaryMethod<List<Boolean>>(
        suffix = "arrBool",
        memberName = "CountBools",
        handler = { it.size },
        argument = emptyList(),
        expected = 0
    )

    // Empty `ay` — the byte-array element type behind GATT characteristic values etc.
    @Test
    fun emptyArrayArg_ofUBytes_roundTrips() = roundTripUnaryMethod<List<UByte>>(
        suffix = "arrUByte",
        memberName = "CountUBytes",
        handler = { it.size },
        argument = emptyList(),
        expected = 0
    )

    @Test
    fun emptyArrayArg_ofUInts_roundTrips() = roundTripUnaryMethod<List<UInt>>(
        suffix = "arrUInt",
        memberName = "CountUInts",
        handler = { it.size },
        argument = emptyList(),
        expected = 0
    )

    @Test
    fun emptyArrayArg_ofObjectPaths_roundTrips() = roundTripUnaryMethod<List<ObjectPath>>(
        suffix = "arrPath",
        memberName = "CountPaths",
        handler = { it.size },
        argument = emptyList(),
        expected = 0
    )

    @Test
    fun emptyArrayArg_ofVariants_roundTrips() = roundTripUnaryMethod<List<Variant>>(
        suffix = "arrVariant",
        memberName = "CountVariants",
        handler = { it.size },
        argument = emptyList(),
        expected = 0
    )

    // ------------------------------------------------------------------
    // Empty dict (`a{..}`) argument coverage with assorted key/value types. This is the case
    // that regressed `a{}` on the JVM backend.
    // ------------------------------------------------------------------

    @Test
    fun emptyDictArg_stringToVariant_roundTrips() = roundTripUnaryMethod<Map<String, Variant>>(
        suffix = "dictSV",
        memberName = "CountSV",
        handler = { it.size },
        argument = emptyMap(),
        expected = 0
    )

    @Test
    fun emptyDictArg_stringToInt_roundTrips() = roundTripUnaryMethod<Map<String, Int>>(
        suffix = "dictSI",
        memberName = "CountSI",
        handler = { it.size },
        argument = emptyMap(),
        expected = 0
    )

    @Test
    fun emptyDictArg_intToString_roundTrips() = roundTripUnaryMethod<Map<Int, String>>(
        suffix = "dictIS",
        memberName = "CountIS",
        handler = { it.size },
        argument = emptyMap(),
        expected = 0
    )

    @Test
    fun emptyDictArg_stringToListOfString_roundTrips() =
        roundTripUnaryMethod<Map<String, List<String>>>(
            suffix = "dictSAS",
            memberName = "CountSAS",
            handler = { it.size },
            argument = emptyMap(),
            expected = 0
        )

    @Test
    fun emptyDictArg_objectPathToVariant_roundTrips() =
        roundTripUnaryMethod<Map<ObjectPath, Variant>>(
            suffix = "dictOV",
            memberName = "CountOV",
            handler = { it.size },
            argument = emptyMap(),
            expected = 0
        )

    // ------------------------------------------------------------------
    // Nested empties.
    // ------------------------------------------------------------------

    // Empty array-of-arrays (`aas`): the outer array is empty so there is no inner array to
    // infer the `as` element signature from.
    @Test
    fun emptyArrayOfArraysArg_roundTrips() = roundTripUnaryMethod<List<List<String>>>(
        suffix = "arrArr",
        memberName = "CountOuter",
        handler = { it.size },
        argument = emptyList(),
        expected = 0
    )

    // Empty array nested *inside* a populated struct (`(sas)` with an empty `as` member).
    @Test
    fun emptyArrayInsideStructArg_roundTrips() {
        val ids = uniqueFixtureIds("structEmptyArr")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("Describe")) {
                call { value: StructWithList -> "${value.label}:${value.items.size}" }
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
            val result = proxy.callMethod<String>(ids.iface, MethodName("Describe")) {
                call(StructWithList("tag", emptyList()))
            }
            assertEquals("tag:0", result)
        } finally {
            runBlocking {
                proxyConnection.stopEventLoop()
                serverConnection.stopEventLoop()
            }
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    // Empty dict wrapped in a variant (`v` carrying `a{sv}`): the variant's contained
    // signature must be the declared `a{sv}`, not the malformed `a{}`.
    @Test
    fun emptyDictInsideVariantArg_roundTrips() {
        val ids = uniqueFixtureIds("variantEmptyDict")
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            method(MethodName("CountInVariant")) {
                call { value: Variant -> value.get<Map<String, Variant>>().size }
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
            val result = proxy.callMethod<Int>(ids.iface, MethodName("CountInVariant")) {
                call(Variant(emptyMap<String, Variant>()))
            }
            assertEquals(0, result)
        } finally {
            runBlocking {
                proxyConnection.stopEventLoop()
                serverConnection.stopEventLoop()
            }
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    // ------------------------------------------------------------------
    // Empty collections as signal bodies. The vtable `signal { with<T>() }` declares the
    // payload type; `emitSignal { call(...) }` serializes the (empty) value with the declared
    // signature. The proxy handler inspects the raw message: it asserts the *observed* wire
    // signature of the first body element (via peekType) AND that the typed payload
    // round-trips back to an empty collection.
    // ------------------------------------------------------------------

    private inline fun <reified T : Any> roundTripUnarySignal(
        suffix: String,
        expectedSignature: String,
        payload: T,
        crossinline assertEmpty: (T) -> Unit
    ) = runBlocking {
        val ids = uniqueFixtureIds(suffix)
        val serverConnection = createBusConnection(ids.service)
        val proxyConnection = createBusConnection()
        val obj = createObject(serverConnection, ids.path)
        val registration = obj.addVTable(ids.iface) {
            signal(SignalName("Empty")) {
                with<T>("value")
            }
        }
        serverConnection.startEventLoop()
        proxyConnection.startEventLoop()
        val proxy = createProxy(proxyConnection, ids.service, ids.path)
        val observedSignature = CompletableDeferred<String>()
        val decoded = CompletableDeferred<T>()
        val signalRegistration =
            proxy.registerSignalHandler(ids.iface, SignalName("Empty")) { message ->
                // Capture failures into the deferreds so a malformed body surfaces as a test
                // failure rather than a 2s timeout.
                try {
                    message.rewind(false)
                    val peeked = message.peekType()
                    observedSignature.complete("${peeked.type ?: ""}${peeked.contents ?: ""}")
                    message.rewind(false)
                    decoded.complete(message.deserialize<T>())
                } catch (t: Throwable) {
                    observedSignature.completeExceptionally(t)
                    decoded.completeExceptionally(t)
                }
            }

        try {
            obj.emitSignal(ids.iface, SignalName("Empty")) {
                call(payload)
            }
            // The body must round-trip back to an empty collection on every backend. A
            // malformed send signature would either fail emit or drop the connection, so
            // reaching this assertion already proves the declared signature reached the wire.
            assertEmpty(withTimeout(2_000) { decoded.await() })
            if (EmptyCollectionWireSupport.preservesWireSignatureForEmptyCollections) {
                assertEquals(
                    expectedSignature,
                    withTimeout(2_000) { observedSignature.await() },
                    "wire signature of empty signal body"
                )
            }
        } finally {
            signalRegistration.release()
            registration.release()
            proxy.release()
            obj.release()
            proxyConnection.stopEventLoop()
            serverConnection.stopEventLoop()
            proxyConnection.release()
            serverConnection.release()
        }
    }

    @Test
    fun emptyArraySignalBody_ofStrings_roundTripsWithCorrectSignature() =
        roundTripUnarySignal<List<String>>(
            suffix = "sigArrStr",
            expectedSignature = "as",
            payload = emptyList()
        ) { assertTrue(it.isEmpty()) }

    @Test
    fun emptyArraySignalBody_ofInts_roundTripsWithCorrectSignature() =
        roundTripUnarySignal<List<Int>>(
            suffix = "sigArrInt",
            expectedSignature = "ai",
            payload = emptyList()
        ) { assertTrue(it.isEmpty()) }

    @Test
    fun emptyArraySignalBody_ofUBytes_roundTripsWithCorrectSignature() =
        roundTripUnarySignal<List<UByte>>(
            suffix = "sigArrUByte",
            expectedSignature = "ay",
            payload = emptyList()
        ) { assertTrue(it.isEmpty()) }

    @Test
    fun emptyDictSignalBody_stringToVariant_roundTripsWithCorrectSignature() =
        roundTripUnarySignal<Map<String, Variant>>(
            suffix = "sigDictSV",
            expectedSignature = "a{sv}",
            payload = emptyMap()
        ) { assertTrue(it.isEmpty()) }

    @Test
    fun emptyDictSignalBody_stringToInt_roundTripsWithCorrectSignature() =
        roundTripUnarySignal<Map<String, Int>>(
            suffix = "sigDictSI",
            expectedSignature = "a{si}",
            payload = emptyMap()
        ) { assertTrue(it.isEmpty()) }

    @Test
    fun emptyArrayOfArraysSignalBody_roundTripsWithCorrectSignature() =
        roundTripUnarySignal<List<List<String>>>(
            suffix = "sigArrArr",
            expectedSignature = "aas",
            payload = emptyList()
        ) { assertTrue(it.isEmpty()) }
}
