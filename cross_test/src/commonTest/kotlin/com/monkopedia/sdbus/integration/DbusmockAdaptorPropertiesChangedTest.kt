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

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertiesProxy
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.notifying
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.setProperty
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout

/**
 * A minimal server-side property holder mirroring what the codegen emits for a writable
 * `EmitsChangedSignal` property: the backing field is delegated to [notifying], so assigning it —
 * locally OR via the vtable setter a remote `Properties.Set` is routed through — auto-emits
 * `PropertiesChanged`.
 */
private class LevelService(obj: Object, iface: InterfaceName, property: PropertyName) {
    var level: Int by obj.notifying(iface, property, 0)
}

/**
 * End-to-end coverage (issue #115) that a generated-adaptor-style writable property auto-emits
 * `org.freedesktop.DBus.Properties.PropertiesChanged` on BOTH paths:
 *  - (a) a remote `Properties.Set` from a client, routed through the vtable setter; and
 *  - (b) a server-side assignment of the property in our own code.
 *
 * We export our OWN object (the `notifying` delegate as the property backing + `with(::level)` in
 * the vtable, exactly as codegen wires it) and observe a client's `PropertiesChanged` subscription.
 * Runs in commonTest so the assertions exercise BOTH the native sd-bus backend (`linuxX64Test`) and
 * the JVM wire backend (`jvmTest`).
 *
 * Gated through [withDbusmockPeer]: it skips cleanly unless python-dbusmock is available, so set
 * `DBUSMOCK_PYTHON` (see [DbusmockHarness]) to actually run it locally; CI's wire-client-parity job
 * is the authoritative check. The peer guarantees a real, independently-validated session bus; the
 * remote `Properties.Set` and subscription are driven over that bus by our own client connection.
 */
class DbusmockAdaptorPropertiesChangedTest {

    @Test
    fun propertiesChangedFires_onRemoteSet_andServerSideSet() = withDbusmockPeer("AdaptorProps") {
        val id = Random.nextInt(100_000, 999_999)
        val serviceName = ServiceName("com.monkopedia.sdbus.adaptorprops$id")
        val path = ObjectPath("/com/monkopedia/sdbus/adaptorprops$id")
        val iface = InterfaceName("com.monkopedia.sdbus.AdaptorProps")
        val property = PropertyName("Level")

        val serverConnection = createBusConnection(serviceName)
        val obj = createObject(serverConnection, path)
        val service = LevelService(obj, iface, property)
        val registration = obj.addVTable(iface) {
            prop(property) {
                with(service::level)
            }
        }
        serverConnection.startEventLoop()

        // `connection` is the dbusmock harness's live client connection on the same session bus.
        val proxy = createProxy(connection, serviceName, path)
        val events =
            Channel<Pair<Map<PropertyName, Variant>, List<PropertyName>>>(Channel.UNLIMITED)
        PropertiesProxy(proxy).registerPropertiesProxy { changedInterface, changed, invalidated ->
            if (changedInterface == iface) events.trySend(changed to invalidated)
        }

        try {
            // (a) Remote Properties.Set: dispatched to the vtable setter, which writes the
            // notifying-delegated backing field and emits PropertiesChanged.
            proxy.setProperty(iface, property, 5)
            val remote = withTimeout(10_000) { events.receive() }
            assertEquals(
                5,
                remote.first[property]?.get<Int>(),
                "remote Set must emit the new value"
            )

            // (b) Server-side assignment: the delegate emits directly.
            service.level = 9
            val serverSide = withTimeout(10_000) { events.receive() }
            assertEquals(
                9,
                serverSide.first[property]?.get<Int>(),
                "server-side set must emit the new value"
            )
        } finally {
            proxy.release()
            registration.release()
            obj.release()
            serverConnection.stopEventLoop()
            serverConnection.release()
        }
    }
}
