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

import com.monkopedia.sdbus.Connection
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertiesProxy
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createProxy
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** The python-dbusmock runtime-scripting control interface. */
internal val MOCK_INTERFACE = InterfaceName("org.freedesktop.DBus.Mock")

/**
 * A scripted python-dbusmock peer plus the sdbus-kotlin client side talking to it: [control]
 * drives the `org.freedesktop.DBus.Mock` scripting interface while [proxy] is used for the
 * actual assertions against the mocked [iface]. See [DbusmockHarness] for the underlying
 * process management and [withDbusmockPeer] for the lifecycle.
 */
internal class DbusmockPeer(
    val connection: Connection,
    val control: Proxy,
    val proxy: Proxy,
    val busName: ServiceName,
    val objectPath: ObjectPath,
    val iface: InterfaceName
) {
    /** Scripts a method on the peer: dbusmock executes [code] (Python) when it is called. */
    fun addMethod(name: String, inSig: String, outSig: String, code: String) {
        control.callMethod<Unit>(MOCK_INTERFACE, MethodName("AddMethod")) {
            call(iface.value, name, inSig, outSig, code)
        }
    }

    /** Scripts an identity method that replies with its single argument unchanged. */
    fun addEcho(name: String, signature: String) =
        addMethod(name, signature, signature, "ret = args[0]")

    /** Adds a property on the peer's main interface (no PropertiesChanged is emitted). */
    fun addProperty(name: String, value: Variant) {
        control.callMethod<Unit>(MOCK_INTERFACE, MethodName("AddProperty")) {
            call(iface.value, name, value)
        }
    }

    /**
     * Updates properties on the peer's main interface. dbusmock emits a real
     * `org.freedesktop.DBus.Properties.PropertiesChanged` signal carrying the new values.
     */
    fun updateProperties(properties: Map<String, Variant>) {
        control.callMethod<Unit>(MOCK_INTERFACE, MethodName("UpdateProperties")) {
            call(iface.value, properties)
        }
    }

    /**
     * Makes the peer emit an arbitrary signal: [args] are variant-wrapped values that dbusmock
     * unpacks according to [signature] (one variant per top-level signature element).
     */
    fun emitSignal(
        interfaceName: String,
        name: String,
        signature: String,
        args: List<Variant> = emptyList()
    ) {
        control.callMethod<Unit>(MOCK_INTERFACE, MethodName("EmitSignal")) {
            call(interfaceName, name, signature, args)
        }
    }

    /** [emitSignal] on the peer's main interface. */
    fun emitSignal(name: String, signature: String, args: List<Variant> = emptyList()) =
        emitSignal(iface.value, name, signature, args)

    /**
     * Makes the peer emit a `PropertiesChanged` signal for its main interface that lists
     * [invalidated] property names as invalidated (no new value carried) alongside the
     * optionally non-empty [changed] map. dbusmock's own `UpdateProperties` only ever emits
     * changed values, so invalidation is scripted through the generic signal emitter.
     */
    fun emitPropertiesChanged(
        invalidated: List<String>,
        changed: Map<String, Variant> = emptyMap()
    ) = emitSignal(
        PropertiesProxy.INTERFACE_NAME.value,
        "PropertiesChanged",
        "sa{sv}as",
        listOf(Variant(iface.value), Variant(changed), Variant(invalidated))
    )
}

/**
 * Flow-based subscribers ([com.monkopedia.sdbus.signalFlow] and everything built on it, like
 * `PropertyDelegate.changes()`) register their signal handler only once collection starts,
 * which the test cannot otherwise observe. This repeatedly invokes [poke] (which must
 * eventually cause an element to land in [received]) until the first element arrives —
 * proving the subscription is live — and consumes that element.
 */
internal suspend fun <T> pumpUntilSubscribed(received: ReceiveChannel<T>, poke: () -> Unit): Unit =
    withTimeout(15_000) {
        var first: T? = null
        while (first == null) {
            poke()
            first = withTimeoutOrNull(250) { received.receive() }
        }
    }

/**
 * Launches a fresh dbusmock peer (unique bus name / path / interface), waits for it to claim
 * its name, runs [block] against it, and tears everything down. If python-dbusmock is not
 * available the test SKIPs cleanly (runs no assertions).
 *
 * @param suffix Distinguishes the peer's bus name / object path between tests.
 * @param objectManager When `true`, the peer also implements
 *   `org.freedesktop.DBus.ObjectManager` at [DbusmockPeer.objectPath] (dbusmock `-m`).
 */
internal fun withDbusmockPeer(
    suffix: String,
    objectManager: Boolean = false,
    block: suspend DbusmockPeer.() -> Unit
) = runBlocking {
    val id = Random.nextInt(100_000, 999_999)
    val busName = "com.monkopedia.sdbus.dbusmock.$suffix$id"
    val objectPath = "/com/monkopedia/sdbus/dbusmock/$suffix$id"

    val handle = launchDbusmock(busName, objectPath, busName, objectManager)
    if (handle == null) {
        println(
            "[withDbusmockPeer] SKIP: python-dbusmock unavailable. " +
                "Install via 'apt install python3-dbusmock' / 'pip install python-dbusmock' " +
                "(see DbusmockHarness KDoc)."
        )
        return@runBlocking
    }

    val connection: Connection = createBusConnection()
    connection.startEventLoop()
    val control = createProxy(connection, ServiceName(busName), ObjectPath(objectPath))
    val proxy = createProxy(connection, ServiceName(busName), ObjectPath(objectPath))
    val peer = DbusmockPeer(
        connection,
        control,
        proxy,
        ServiceName(busName),
        ObjectPath(objectPath),
        InterfaceName(busName)
    )

    try {
        // dbusmock takes a moment to claim its bus name; retry a no-op control call until
        // the name is owned (or we time out).
        dbusmockRetry(timeoutMillis = 15_000) {
            peer.addMethod("Ready", "", "i", "ret = 0")
        }
        peer.block()
    } finally {
        proxy.release()
        control.release()
        connection.stopEventLoop()
        connection.release()
        handle.stop()
    }
}

/** Repeats [block] (spaced by a busy-wait) until it stops throwing or [timeoutMillis] passes. */
internal inline fun dbusmockRetry(timeoutMillis: Long, block: () -> Unit) {
    val start = TimeSource.Monotonic.markNow()
    var last: Throwable? = null
    while (start.elapsedNow().inWholeMilliseconds < timeoutMillis) {
        val result = runCatching(block)
        if (result.isSuccess) return
        last = result.exceptionOrNull()
        busyWait(100)
    }
    throw AssertionError("Timed out waiting for dbusmock to become ready", last)
}
