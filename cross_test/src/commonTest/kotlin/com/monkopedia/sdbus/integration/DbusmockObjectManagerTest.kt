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
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectManagerProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.onSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * ObjectManager coverage against the python-dbusmock independent peer (issue #36): the peer is
 * launched with dbusmock's `-m` flag so it implements `org.freedesktop.DBus.ObjectManager` for
 * the objects it adds/removes via its `AddObject`/`RemoveObject` scripting calls.
 *
 * Verifies the raw `InterfacesAdded`/`InterfacesRemoved` payloads emitted by the foreign stack,
 * the `GetManagedObjects` round-trip, and that [ObjectManagerProxy]'s reactive state flows
 * converge on the same view.
 *
 * Lives in commonTest so the exact same assertions run against BOTH the native sd-bus backend
 * (`linuxX64Test`) and the JVM dbus-java backend (`jvmTest`). Skips cleanly when
 * python3-dbusmock is not installed (see [DbusmockHarness]).
 */
class DbusmockObjectManagerTest {

    @Test
    fun interfacesAddedAndRemoved_trackForeignObjectLifecycle() =
        withDbusmockPeer("ObjMgr", objectManager = true) {
            val childPath = ObjectPath("${objectPath.value}/child0")
            val childIface = InterfaceName("${iface.value}.Child")
            addSpawnAndDropMethods(childIface)

            // Raw signal subscriptions, registered before the peer adds any object.
            val added = CompletableDeferred<Pair<ObjectPath, InterfacesAndProperties>>()
            val removed = CompletableDeferred<Pair<ObjectPath, List<InterfaceName>>>()
            val addedRegistration = proxy.onSignal(
                ObjectManagerProxy.INTERFACE_NAME,
                SignalName("InterfacesAdded")
            ) {
                call { path: ObjectPath, ifaces: InterfacesAndProperties ->
                    added.complete(path to ifaces)
                }
            }
            val removedRegistration = proxy.onSignal(
                ObjectManagerProxy.INTERFACE_NAME,
                SignalName("InterfacesRemoved")
            ) {
                call { path: ObjectPath, ifaces: List<InterfaceName> ->
                    removed.complete(path to ifaces)
                }
            }

            try {
                val om = ObjectManagerProxy(proxy)
                assertTrue(
                    om.getManagedObjects().isEmpty(),
                    "peer should manage no objects before AddObject"
                )

                proxy.callMethod<Unit>(iface, MethodName("SpawnChild")) { call(childPath.value) }

                // Raw InterfacesAdded payload decoded from the foreign emitter.
                val (addedPath, addedIfaces) = withTimeout(10_000) { added.await() }
                assertEquals(childPath, addedPath)
                val signalProps = assertNotNull(
                    addedIfaces[childIface],
                    "child interface missing from InterfacesAdded payload"
                )
                assertEquals("first-child", signalProps[PropertyName("Name")]?.get<String>())
                assertEquals(7, signalProps[PropertyName("Level")]?.get<Int>())

                // ObjectManagerProxy's reactive flows converge on the same view.
                withTimeout(10_000) { om.objects.first { childPath in it } }
                withTimeout(10_000) { om.interfacesFor(childPath).first { childIface in it } }
                withTimeout(10_000) { om.objectsFor(childIface).first { childPath in it } }
                val data =
                    withTimeout(10_000) { om.objectData(childPath).first { it.isNotEmpty() } }
                assertEquals(7, data[childIface]?.get(PropertyName("Level"))?.get<Int>())

                // GetManagedObjects agrees with the signal-tracked state.
                val managed = om.getManagedObjects()
                val managedProps = assertNotNull(
                    managed[childPath]?.get(childIface),
                    "child missing from GetManagedObjects"
                )
                assertEquals("first-child", managedProps[PropertyName("Name")]?.get<String>())
                assertEquals(7, managedProps[PropertyName("Level")]?.get<Int>())

                // Removal: raw payload carries the dropped interface names...
                proxy.callMethod<Unit>(iface, MethodName("DropChild")) { call(childPath.value) }
                val (removedPath, removedIfaces) = withTimeout(10_000) { removed.await() }
                assertEquals(childPath, removedPath)
                assertEquals(listOf(childIface), removedIfaces)

                // ...and both the proxy state and the peer agree the object is gone. (The
                // path itself intentionally stays in [ObjectManagerProxy.objects] with no
                // interfaces; interfacesFor is the lifecycle-accurate view.)
                withTimeout(10_000) { om.interfacesFor(childPath).first { it.isEmpty() } }
                assertTrue(
                    om.getManagedObjects().isEmpty(),
                    "peer should manage no objects after RemoveObject"
                )
            } finally {
                addedRegistration.release()
                removedRegistration.release()
            }
        }

    @Test
    fun objectManagerProxy_seedsInitialStateFromGetManagedObjects() =
        withDbusmockPeer("ObjMgrSeed", objectManager = true) {
            val childIface = InterfaceName("${iface.value}.Child")
            addSpawnAndDropMethods(childIface)
            val childA = ObjectPath("${objectPath.value}/a")
            val childB = ObjectPath("${objectPath.value}/b")
            proxy.callMethod<Unit>(iface, MethodName("SpawnChild")) { call(childA.value) }
            proxy.callMethod<Unit>(iface, MethodName("SpawnChild")) { call(childB.value) }

            // Both children exist before the ObjectManagerProxy is created: its initial state
            // must come from GetManagedObjects, not from signals (which were never observed).
            val om = ObjectManagerProxy(proxy)
            assertEquals(setOf(childA, childB), om.objects.first().toSet())
            assertEquals(listOf(childIface), om.interfacesFor(childA).first())
            assertEquals(
                "first-child",
                om.objectData(childB).first()[childIface]?.get(PropertyName("Name"))?.get<String>()
            )
        }

    /**
     * Scripts `SpawnChild`/`DropChild` methods on the peer that add/remove a child object with
     * interface [childIface] (properties `Name`/`Level`) and emit the corresponding
     * `InterfacesAdded`/`InterfacesRemoved` signals from the foreign ObjectManager.
     */
    private fun DbusmockPeer.addSpawnAndDropMethods(childIface: InterfaceName) {
        addMethod(
            "SpawnChild",
            "s",
            "",
            "self.AddObject(args[0], '${childIface.value}', " +
                "{'Name': dbus.String('first-child'), 'Level': dbus.Int32(7)}, []); " +
                "self.object_manager_emit_added(args[0])"
        )
        addMethod(
            "DropChild",
            "s",
            "",
            "self.object_manager_emit_removed(args[0]); self.RemoveObject(args[0])"
        )
    }
}

/** The `a{sa{sv}}` payload of `InterfacesAdded` / a `GetManagedObjects` entry. */
private typealias InterfacesAndProperties = Map<InterfaceName, Map<PropertyName, Variant>>
