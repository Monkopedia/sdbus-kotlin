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
import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MutablePropertyDelegate
import com.monkopedia.sdbus.ObjectManagerProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertiesProxy
import com.monkopedia.sdbus.PropertyDelegate
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.mutableDelegate
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.propDelegate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Hardware-free BlueZ integration suite (issue #33) driving sdbus-kotlin clients against the
 * python-dbusmock **`bluez5` template** — an independent, BlueZ-shaped `org.bluez` peer — on
 * both backends. This reproduces, in CI, the choreography that previously needed a real
 * adapter + BLE peripheral: ObjectManager discovery over the deeply nested
 * `a{oa{sa{sv}}}` GetManagedObjects shape, `Adapter1` power/discovery, device appearance via
 * `InterfacesAdded`, `Device1` Connect/Disconnect/Pair with `PropertiesChanged`, and the GATT
 * service/characteristic flows (discovery, ReadValue/WriteValue, StartNotify → value stream).
 *
 * ## What the template provides vs. what is scripted
 *
 * dbusmock 0.38.1's `bluez5` template ships `AddAdapter`/`AddDevice` (full `Adapter1`/`Device1`
 * property sets, ObjectManager signals), `StartDiscovery`/`StopDiscovery`, `Connect` /
 * `Disconnect`/`Pair`, and `ConnectDevice`/`DisconnectDevice`/`PairDevice` conveniences — but
 * **no GATT objects at all** (no `GattService1`/`GattCharacteristic1`). The GATT tree is
 * therefore scripted onto the same mock through the generic `org.freedesktop.DBus.Mock`
 * control interface (the technique established by [DbusmockSecretServiceTest]): real objects
 * with the BlueZ GATT interface names/properties, `ReadValue`/`WriteValue`/`StartNotify` /
 * `StopNotify` methods backed by the mock's property store, and notifications surfaced as
 * `Value` PropertiesChanged — the exact shape BlueZ uses. `AcquireWrite`/`AcquireNotify`
 * (unix-fd + grouped returns) and GATT descriptors are intentionally out of scope.
 *
 * ## Bus selection
 *
 * The template declares `SYSTEM_BUS = True`; the harness always passes `--session`, which
 * overrides that flag and keeps the mock on the private `dbus-run-session` bus the tests
 * already use — no private system bus needed (see [launchDbusmock]).
 *
 * ## Template quirks (0.38.1) worked around here
 *
 * - `StartDiscovery` raises a KeyError unless `SetDiscoveryFilter` was called first (the
 *   `DiscoveryFilter` property is read but never initialised). Real BlueZ clients set a
 *   filter before scanning anyway, so the tests do the same.
 * - `Device1.Connect()`/`Disconnect()` emit `PropertiesChanged` for `Connected` but do **not**
 *   update the property store (a `Get` still returns the old value). Signal payloads are
 *   asserted for the real methods; property-backed state uses the `ConnectDevice` /
 *   `DisconnectDevice` mock conveniences, which update both.
 *
 * ## Known backend gaps (gated, not re-tripped)
 *
 * The BlueZ wire profile is dicts/object-paths/arrays — no structs (#71) and no multi-out
 * methods (#74) anywhere in the covered surface, so only issue #72 needs gating:
 * `org.bluez.Error.*` error-name assertions run behind [peerErrorNameMappingSupported] while
 * the `assertFailsWith<Error>` part always runs on both backends.
 *
 * Lives in commonTest so the exact same assertions run against BOTH the native sd-bus backend
 * (`linuxX64Test`) and the JVM dbus-java backend (`jvmTest`). Skips cleanly when
 * python3-dbusmock is not installed (see [DbusmockHarness]).
 */
class DbusmockBluezTest {

    // --- ObjectManager tree: the a{oa{sa{sv}}} stress case -----------------------------------

    @Test
    fun getManagedObjects_decodesFullBluezTree() = withBluezMock {
        val adapterPath = addAdapter("hci0", "mock-host")
        val phonePath = addDevice("hci0", PHONE_ADDRESS, "Phone")
        val sensorPath = addDevice("hci0", SENSOR_ADDRESS, "BF-Test")
        assertEquals(ObjectPath("/org/bluez/hci0"), adapterPath)
        assertEquals(ObjectPath("/org/bluez/hci0/dev_11_22_33_44_55_66"), phonePath)

        val managed = ObjectManagerProxy(root).getManagedObjects()
        assertEquals(
            setOf(ObjectPath("/org/bluez"), adapterPath, phonePath, sensorPath),
            managed.keys
        )
        assertTrue(
            AGENT_MANAGER1 in managed.getValue(ObjectPath("/org/bluez")),
            "manager object should expose AgentManager1"
        )

        // Adapter entry: nested a{sv} with strings, booleans, uint32s and string arrays.
        val adapterIfaces = managed.getValue(adapterPath)
        val adapter = assertNotNull(adapterIfaces[ADAPTER1], "Adapter1 missing from tree")
        // hci0 -> the template derives the address from the trailing digit.
        assertEquals("00:01:02:03:04:05", adapter[PropertyName("Address")]?.get<String>())
        assertEquals("mock-host", adapter[PropertyName("Name")]?.get<String>())
        assertEquals(true, adapter[PropertyName("Powered")]?.get<Boolean>())
        assertEquals(false, adapter[PropertyName("Discovering")]?.get<Boolean>())
        assertEquals(268u, adapter[PropertyName("Class")]?.get<UInt>())
        assertEquals(
            listOf("central", "peripheral"),
            adapter[PropertyName("Roles")]?.get<List<String>>()
        )
        assertEquals(5, adapter[PropertyName("UUIDs")]?.get<List<String>>()?.size)
        // The template also exposes the advertising-related adapter interfaces.
        assertTrue(LE_ADVERTISING_MANAGER1 in adapterIfaces, "LEAdvertisingManager1 missing")

        // Device entry: int16 RSSI/TxPower, uint16 Appearance, object-path Adapter link.
        val device = assertNotNull(managed.getValue(phonePath)[DEVICE1], "Device1 missing")
        assertEquals(PHONE_ADDRESS, device[PropertyName("Address")]?.get<String>())
        assertEquals("Phone", device[PropertyName("Name")]?.get<String>())
        assertEquals("Phone", device[PropertyName("Alias")]?.get<String>())
        assertEquals((-79).toShort(), device[PropertyName("RSSI")]?.get<Short>())
        assertEquals(0.toShort(), device[PropertyName("TxPower")]?.get<Short>())
        assertEquals(0u.toUShort(), device[PropertyName("Appearance")]?.get<UShort>())
        assertEquals(false, device[PropertyName("Paired")]?.get<Boolean>())
        assertEquals(false, device[PropertyName("Connected")]?.get<Boolean>())
        assertEquals(false, device[PropertyName("ServicesResolved")]?.get<Boolean>())
        assertEquals(adapterPath, device[PropertyName("Adapter")]?.get<ObjectPath>())

        // The same values through typed proxies (codegen consumption style, Properties.Get).
        proxyAt(adapterPath) { proxy ->
            val typed = Adapter1Proxy(proxy)
            assertEquals("00:01:02:03:04:05", typed.address)
            assertEquals("mock-host", typed.name)
            assertTrue(typed.powered)
            assertFalse(typed.discovering)
        }
        proxyAt(sensorPath) { proxy ->
            val typed = Device1Proxy(proxy)
            assertEquals(SENSOR_ADDRESS, typed.address)
            assertEquals("BF-Test", typed.alias)
            assertEquals((-79).toShort(), typed.rssi)
            assertEquals(adapterPath, typed.adapter)
            assertFalse(typed.connected)
            assertFalse(typed.servicesResolved)
        }
    }

    // --- Adapter1: Powered via Properties.Set + discovery lifecycle --------------------------

    @Test
    fun adapter_poweredAndDiscovery_roundTripWithPropertiesChanged() = withBluezMock {
        val adapterPath = addAdapter("hci0", "mock-host")
        proxyAt(adapterPath) { proxy ->
            val adapter = Adapter1Proxy(proxy)
            val events = PropertyEventStream(proxy)

            // Powered off/on through org.freedesktop.DBus.Properties.Set on the foreign peer;
            // the mock confirms each write with a PropertiesChanged carrying the new value.
            adapter.powered = false
            assertEquals(false, events.awaitChanged(ADAPTER1, "Powered").get<Boolean>())
            assertFalse(adapter.powered)
            adapter.powered = true
            assertEquals(true, events.awaitChanged(ADAPTER1, "Powered").get<Boolean>())
            assertTrue(adapter.powered)

            // TEMPLATE QUIRK (see class KDoc): StartDiscovery KeyErrors unless a discovery
            // filter exists; set one first like a real BlueZ scan client would.
            adapter.setDiscoveryFilter(mapOf("Transport" to Variant("le")))
            adapter.startDiscovery()
            assertEquals(true, events.awaitChanged(ADAPTER1, "Discovering").get<Boolean>())
            assertTrue(adapter.discovering)

            adapter.stopDiscovery()
            assertEquals(false, events.awaitChanged(ADAPTER1, "Discovering").get<Boolean>())
            assertFalse(adapter.discovering)
        }
    }

    // --- Device appearance / removal through ObjectManager signals ---------------------------

    @Test
    fun objectManager_tracksDeviceAppearanceAndRemoval() = withBluezMock {
        val adapterPath = addAdapter("hci0", "mock-host")
        val om = ObjectManagerProxy(root)

        val added = CompletableDeferred<Pair<ObjectPath, IfacesAndProps>>()
        val removed = CompletableDeferred<Pair<ObjectPath, List<InterfaceName>>>()
        val registrations = listOf(
            root.onSignal(ObjectManagerProxy.INTERFACE_NAME, SignalName("InterfacesAdded")) {
                call { path: ObjectPath, ifaces: IfacesAndProps ->
                    added.complete(path to ifaces)
                }
            },
            root.onSignal(ObjectManagerProxy.INTERFACE_NAME, SignalName("InterfacesRemoved")) {
                call { path: ObjectPath, ifaces: List<InterfaceName> ->
                    removed.complete(path to ifaces)
                }
            }
        )

        try {
            // A device appears mid-scan: the InterfacesAdded payload carries its full
            // Device1 property set from the foreign emitter.
            val devicePath = addDevice("hci0", SENSOR_ADDRESS, "BF-Test")
            val (addedPath, addedIfaces) = withTimeout(10_000) { added.await() }
            assertEquals(devicePath, addedPath)
            val props = assertNotNull(
                addedIfaces[DEVICE1],
                "Device1 missing from InterfacesAdded payload"
            )
            assertEquals(SENSOR_ADDRESS, props[PropertyName("Address")]?.get<String>())
            assertEquals("BF-Test", props[PropertyName("Name")]?.get<String>())
            assertEquals((-79).toShort(), props[PropertyName("RSSI")]?.get<Short>())
            assertEquals(adapterPath, props[PropertyName("Adapter")]?.get<ObjectPath>())

            // The reactive adopter view (samples/bluez-scan pattern) converges on the device.
            withTimeout(10_000) { om.objectsFor(DEVICE1).first { devicePath in it } }

            // Removing through the real Adapter1.RemoveDevice call emits InterfacesRemoved.
            proxyAt(adapterPath) { Adapter1Proxy(it).removeDevice(devicePath) }
            val (removedPath, removedIfaces) = withTimeout(10_000) { removed.await() }
            assertEquals(devicePath, removedPath)
            assertEquals(listOf(DEVICE1), removedIfaces)
            withTimeout(10_000) { om.interfacesFor(devicePath).first { it.isEmpty() } }
        } finally {
            registrations.forEach { it.release() }
        }
    }

    @Test
    fun adapterRemoval_dropsAdapterAndItsDevices() = withBluezMock {
        val adapterPath = addAdapter("hci1", "mock-host")
        val devicePath = addDevice("hci1", PHONE_ADDRESS, "Phone")
        val om = ObjectManagerProxy(root)
        withTimeout(10_000) { om.objectsFor(DEVICE1).first { devicePath in it } }

        val removals = Channel<Pair<ObjectPath, List<InterfaceName>>>(Channel.UNLIMITED)
        val registration =
            root.onSignal(ObjectManagerProxy.INTERFACE_NAME, SignalName("InterfacesRemoved")) {
                call { path: ObjectPath, ifaces: List<InterfaceName> ->
                    // The handler's return value must stay serializable (Unit, not the
                    // ChannelResult trySend returns).
                    removals.trySend(path to ifaces)
                    Unit
                }
            }

        try {
            removeAdapterWithDevices("hci1")
            // The template removes the devices first, then the adapter itself.
            assertEquals(
                devicePath to listOf(DEVICE1),
                withTimeout(10_000) { removals.receive() }
            )
            assertEquals(
                adapterPath to listOf(ADAPTER1),
                withTimeout(10_000) { removals.receive() }
            )
            // The template deliberately announces only [Adapter1] (mimicking a bluez crash,
            // where InterfacesRemoved is incomplete), so the signal-tracked view still lists
            // the adapter's advertising interfaces — clients must handle that. Adapter1
            // itself is gone from the tracked state...
            withTimeout(10_000) { om.interfacesFor(adapterPath).first { ADAPTER1 !in it } }
            // ...and a fresh GetManagedObjects shows the whole object actually dropped.
            assertEquals(
                setOf(ObjectPath("/org/bluez")),
                ObjectManagerProxy(root).getManagedObjects().keys,
                "only the manager object should survive adapter removal"
            )
        } finally {
            registration.release()
        }
    }

    // --- Device1: Connect / Disconnect / Pair -------------------------------------------------

    @Test
    fun device_connectDisconnect_signalsAndErrors() = withBluezMock {
        addAdapter("hci0", "mock-host")
        val devicePath = addDevice("hci0", SENSOR_ADDRESS, "BF-Test")
        proxyAt(devicePath) { proxy ->
            val device = Device1Proxy(proxy)
            val events = PropertyEventStream(proxy)

            // Connect emits PropertiesChanged(Connected=true). (TEMPLATE QUIRK, see class
            // KDoc: the real Connect/Disconnect only emit the signal without updating the
            // property store, so the signal payload is what is asserted here.)
            device.connect()
            assertEquals(true, events.awaitChanged(DEVICE1, "Connected").get<Boolean>())

            // Connecting an already-connected device is rejected.
            val alreadyConnected = assertFailsWith<Error> { device.connect() }
            assertBluezError(alreadyConnected, "org.bluez.Error.AlreadyConnected")

            device.disconnect()
            assertEquals(false, events.awaitChanged(DEVICE1, "Connected").get<Boolean>())
            val notConnected = assertFailsWith<Error> { device.disconnect() }
            assertBluezError(notConnected, "org.bluez.Error.NotConnected")

            // The ConnectDevice/DisconnectDevice conveniences update the property store too,
            // covering the Connected property round-trip.
            connectDevice("hci0", SENSOR_ADDRESS)
            assertEquals(true, events.awaitChanged(DEVICE1, "Connected").get<Boolean>())
            assertTrue(device.connected)
            disconnectDevice("hci0", SENSOR_ADDRESS)
            assertEquals(false, events.awaitChanged(DEVICE1, "Connected").get<Boolean>())
            assertFalse(device.connected)
        }
    }

    @Test
    fun device_pair_updatesPairedAndUuids() = withBluezMock {
        addAdapter("hci0", "mock-host")
        val devicePath = addDevice("hci0", PHONE_ADDRESS, "Phone")
        proxyAt(devicePath) { proxy ->
            val device = Device1Proxy(proxy)
            val events = PropertyEventStream(proxy)
            assertFalse(device.paired)
            assertTrue(device.uuids.isEmpty(), "no service UUIDs before pairing")

            device.pair()
            assertEquals(true, events.awaitChanged(DEVICE1, "Paired").get<Boolean>())
            assertTrue(device.paired)
            assertTrue(device.uuids.isNotEmpty(), "pairing should populate service UUIDs")

            val alreadyPaired = assertFailsWith<Error> { device.pair() }
            assertBluezError(alreadyPaired, "org.bluez.Error.AlreadyExists")
        }
    }

    // --- GATT: scripted service/characteristic tree, read/write, notify ----------------------

    @Test
    fun gatt_treeDiscovery_readWrite_andNotifyValueStream() = withBluezMock {
        addAdapter("hci0", "mock-host")
        val devicePath = addDevice("hci0", SENSOR_ADDRESS, "BF-Test")
        installGattPlumbing()
        val om = ObjectManagerProxy(root)

        val added = Channel<ObjectPath>(Channel.UNLIMITED)
        val registration =
            root.onSignal(ObjectManagerProxy.INTERFACE_NAME, SignalName("InterfacesAdded")) {
                call { path: ObjectPath, _: IfacesAndProps ->
                    // The handler's return value must stay serializable (Unit, not the
                    // ChannelResult trySend returns).
                    added.trySend(path)
                    Unit
                }
            }

        try {
            proxyAt(devicePath) { deviceProxy ->
                val device = Device1Proxy(deviceProxy)
                val deviceEvents = PropertyEventStream(deviceProxy)

                // Connect, then the GATT database appears and ServicesResolved flips — the
                // exact post-connect choreography bluetoothd performs.
                connectDevice("hci0", SENSOR_ADDRESS)
                assertEquals(true, deviceEvents.awaitChanged(DEVICE1, "Connected").get<Boolean>())

                val servicePath = addGattService(devicePath, HRS_UUID)
                val charPath =
                    addGattCharacteristic(servicePath, HRM_UUID, initialValue = listOf(0u, 60u))
                assertEquals(servicePath, withTimeout(10_000) { added.receive() })
                assertEquals(charPath, withTimeout(10_000) { added.receive() })

                resolveServices(devicePath)
                assertEquals(
                    true,
                    deviceEvents.awaitChanged(DEVICE1, "ServicesResolved").get<Boolean>()
                )
                assertTrue(device.servicesResolved)

                // Service/characteristic enumeration via GetManagedObjects (the discovery
                // shape GATT clients like blue-falcon use).
                val managed = om.getManagedObjects()
                val service = assertNotNull(
                    managed[servicePath]?.get(GATT_SERVICE1),
                    "GattService1 missing from managed objects"
                )
                assertEquals(HRS_UUID, service[PropertyName("UUID")]?.get<String>())
                assertEquals(true, service[PropertyName("Primary")]?.get<Boolean>())
                assertEquals(devicePath, service[PropertyName("Device")]?.get<ObjectPath>())
                val characteristic = assertNotNull(
                    managed[charPath]?.get(GATT_CHARACTERISTIC1),
                    "GattCharacteristic1 missing from managed objects"
                )
                assertEquals(HRM_UUID, characteristic[PropertyName("UUID")]?.get<String>())
                assertEquals(
                    servicePath,
                    characteristic[PropertyName("Service")]?.get<ObjectPath>()
                )
                assertEquals(
                    listOf("read", "write", "notify"),
                    characteristic[PropertyName("Flags")]?.get<List<String>>()
                )
                withTimeout(10_000) {
                    om.objectsFor(GATT_CHARACTERISTIC1).first { charPath in it }
                }

                proxyAt(charPath) { charProxy ->
                    val char = GattCharacteristic1Proxy(charProxy)
                    val charEvents = PropertyEventStream(charProxy)
                    assertEquals(HRM_UUID, char.uuid)
                    assertFalse(char.notifying)

                    // ReadValue/WriteValue round-trip (ay payloads + a{sv} options).
                    assertEquals(listOf<UByte>(0u, 60u), char.readValue(emptyMap()))
                    char.writeValue(listOf(1u, 44u), mapOf("type" to Variant("request")))
                    assertEquals(
                        listOf<UByte>(1u, 44u),
                        charEvents.awaitChanged(GATT_CHARACTERISTIC1, "Value").get<List<UByte>>()
                    )
                    assertEquals(listOf<UByte>(1u, 44u), char.readValue(emptyMap()))
                    assertEquals(listOf<UByte>(1u, 44u), char.value)

                    // StartNotify -> Notifying, then notifications flow as Value changes.
                    char.startNotify()
                    assertEquals(
                        true,
                        charEvents.awaitChanged(GATT_CHARACTERISTIC1, "Notifying").get<Boolean>()
                    )
                    assertTrue(char.notifying)
                    injectGattValue(charPath, listOf(0u, 72u))
                    assertEquals(
                        listOf<UByte>(0u, 72u),
                        charEvents.awaitChanged(GATT_CHARACTERISTIC1, "Value").get<List<UByte>>()
                    )
                    injectGattValue(charPath, listOf(0u, 75u))
                    assertEquals(
                        listOf<UByte>(0u, 75u),
                        charEvents.awaitChanged(GATT_CHARACTERISTIC1, "Value").get<List<UByte>>()
                    )
                    char.stopNotify()
                    assertEquals(
                        false,
                        charEvents.awaitChanged(GATT_CHARACTERISTIC1, "Notifying").get<Boolean>()
                    )
                }
            }
        } finally {
            registration.release()
        }
    }

    /**
     * Asserts the `org.bluez.Error.*` name produced by the BlueZ-shaped peer — gated on
     * [peerErrorNameMappingSupported] (issue #72), where the JVM backend is known to discard
     * foreign error names. The fact that an [Error] is thrown at all is asserted
     * unconditionally at each call site via `assertFailsWith`.
     */
    private fun assertBluezError(error: Error, expectedName: String) {
        if (!peerErrorNameMappingSupported) {
            // KNOWN JVM BACKEND BUG (issue #72; see peerErrorNameMappingSupported KDoc).
            println(
                "[DbusmockBluezTest] SKIP error-name assertion for $expectedName: known JVM " +
                    "backend gap (issue #72). Actual name surfaced: ${error.name}"
            )
            return
        }
        assertEquals(expectedName, error.name, "BlueZ error name was not preserved")
    }
}

// --- BlueZ interface names / fixture constants ---------------------------------------------------

private val ADAPTER1 = InterfaceName("org.bluez.Adapter1")
private val DEVICE1 = InterfaceName("org.bluez.Device1")
private val AGENT_MANAGER1 = InterfaceName("org.bluez.AgentManager1")
private val LE_ADVERTISING_MANAGER1 = InterfaceName("org.bluez.LEAdvertisingManager1")
private val GATT_SERVICE1 = InterfaceName("org.bluez.GattService1")
private val GATT_CHARACTERISTIC1 = InterfaceName("org.bluez.GattCharacteristic1")

/** The template's runtime-scripting convenience interface on the root object. */
private val BLUEZ_MOCK = InterfaceName("org.bluez.Mock")

private const val PHONE_ADDRESS = "11:22:33:44:55:66"
private const val SENSOR_ADDRESS = "AA:BB:CC:DD:EE:FF"

/** Heart Rate service / Heart Rate Measurement characteristic, the classic GATT example. */
private const val HRS_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
private const val HRM_UUID = "00002a37-0000-1000-8000-00805f9b34fb"

/** The `a{sa{sv}}` payload of `InterfacesAdded` / a `GetManagedObjects` entry. */
private typealias IfacesAndProps = Map<InterfaceName, Map<PropertyName, Variant>>

// --- Fixture -------------------------------------------------------------------------------------

/**
 * Launches the dbusmock `bluez5` template (claiming the well-known `org.bluez` name on the
 * session bus — see the class KDoc for the `--session` override), waits until the template's
 * `/org/bluez` manager object is up, runs [block], and tears everything down. Skips cleanly
 * (runs no assertions) when python-dbusmock is unavailable.
 */
private fun withBluezMock(block: suspend BluezMock.() -> Unit) = runBlocking {
    val handle = launchDbusmock(
        busName = "org.bluez",
        objectPath = "/",
        interfaceName = ObjectManagerProxy.INTERFACE_NAME.value,
        template = "bluez5"
    )
    if (handle == null) {
        println(
            "[withBluezMock] SKIP: python-dbusmock unavailable. " +
                "Install via 'apt install python3-dbusmock' / 'pip install python-dbusmock' " +
                "(see DbusmockHarness KDoc)."
        )
        return@runBlocking
    }

    val connection: Connection = createBusConnection()
    connection.startEventLoop()
    val root = createProxy(connection, ServiceName("org.bluez"), ObjectPath("/"))
    val mock = BluezMock(connection, root)

    try {
        // The template adds /org/bluez during load; once it shows up in GetManagedObjects the
        // mock has claimed the name and is fully initialised.
        dbusmockRetry(timeoutMillis = 15_000) {
            val managed: Map<ObjectPath, IfacesAndProps> = root.callMethod(
                ObjectManagerProxy.INTERFACE_NAME,
                MethodName("GetManagedObjects")
            ) {}
            check(ObjectPath("/org/bluez") in managed) { "bluez5 template not loaded yet" }
        }
        mock.block()
    } finally {
        root.release()
        connection.stopEventLoop()
        connection.release()
        handle.stop()
    }
}

/**
 * The running bluez5 mock: [root] is the `org.bluez` ObjectManager root, which also carries
 * the template's `org.bluez.Mock` conveniences and the generic dbusmock control interface.
 */
private class BluezMock(val connection: Connection, val root: Proxy) {

    /** Template convenience: creates `/org/bluez/[deviceName]` and emits `InterfacesAdded`. */
    fun addAdapter(deviceName: String, systemName: String): ObjectPath = ObjectPath(
        root.callMethod(BLUEZ_MOCK, MethodName("AddAdapter")) { call(deviceName, systemName) }
    )

    /** Template convenience: creates an unpaired, unconnected `Device1` under the adapter. */
    fun addDevice(adapterName: String, address: String, alias: String): ObjectPath = ObjectPath(
        root.callMethod(BLUEZ_MOCK, MethodName("AddDevice")) { call(adapterName, address, alias) }
    )

    /** Template convenience: sets `Connected=true` in the property store + PropertiesChanged. */
    fun connectDevice(adapterName: String, address: String) {
        root.callMethod<Unit>(BLUEZ_MOCK, MethodName("ConnectDevice")) {
            call(adapterName, address)
        }
    }

    /** Template convenience: sets `Connected=false` in the property store + PropertiesChanged. */
    fun disconnectDevice(adapterName: String, address: String) {
        root.callMethod<Unit>(BLUEZ_MOCK, MethodName("DisconnectDevice")) {
            call(adapterName, address)
        }
    }

    /** Template convenience: removes the adapter and every device underneath it. */
    fun removeAdapterWithDevices(adapterName: String) {
        root.callMethod<Unit>(BLUEZ_MOCK, MethodName("RemoveAdapterWithDevices")) {
            call(adapterName)
        }
    }

    /** Runs [body] against a short-lived proxy for the peer's object at [path]. */
    suspend fun <T> proxyAt(path: ObjectPath, body: suspend (Proxy) -> T): T {
        val proxy = createProxy(connection, ServiceName("org.bluez"), path)
        return try {
            body(proxy)
        } finally {
            proxy.release()
        }
    }

    // --- GATT scripting (the template has no GATT support; see the class KDoc) ---------------

    /**
     * Scripts an `AddGattObject(path, interface, properties)` plumbing method onto the
     * template's mock interface: it creates the object via the generic mock's `AddObject` and
     * emits `InterfacesAdded` from the ObjectManager root, exactly like the template's own
     * `AddAdapter`/`AddDevice`. (Calling the control `AddObject` directly would require a
     * `a(ssss)` struct-array argument, which the JVM backend cannot send — issue #71 — so the
     * object creation happens inside scripted python instead, the technique established by
     * [DbusmockSecretServiceTest].)
     */
    fun installGattPlumbing() {
        root.callMethod<Unit>(MOCK_INTERFACE, MethodName("AddMethod")) {
            call(
                BLUEZ_MOCK.value,
                "AddGattObject",
                "ssa{sv}",
                "",
                "self.AddObject(args[0], args[1], args[2], [])\n" +
                    "self.object_manager_emit_added(args[0])"
            )
        }
    }

    /** Adds a primary `GattService1` under [devicePath], emitting `InterfacesAdded`. */
    fun addGattService(devicePath: ObjectPath, uuid: String): ObjectPath {
        val path = ObjectPath("${devicePath.value}/service0001")
        addGattObject(
            path,
            GATT_SERVICE1,
            mapOf(
                "UUID" to Variant(uuid),
                "Primary" to Variant(true),
                "Device" to Variant(devicePath),
                "Includes" to Variant(emptyList<ObjectPath>())
            )
        )
        return path
    }

    /**
     * Adds a `GattCharacteristic1` under [servicePath] (emitting `InterfacesAdded`) and
     * installs `ReadValue`/`WriteValue`/`StartNotify`/`StopNotify` methods backed by the
     * object's `Value`/`Notifying` properties — writes and notification state changes surface
     * as `PropertiesChanged`, mirroring how bluetoothd exposes GATT characteristics.
     */
    fun addGattCharacteristic(
        servicePath: ObjectPath,
        uuid: String,
        initialValue: List<UByte>
    ): ObjectPath {
        val path = ObjectPath("${servicePath.value}/char0001")
        addGattObject(
            path,
            GATT_CHARACTERISTIC1,
            mapOf(
                "UUID" to Variant(uuid),
                "Service" to Variant(servicePath),
                "Value" to Variant(initialValue),
                "Notifying" to Variant(false),
                "Flags" to Variant(listOf("read", "write", "notify"))
            )
        )
        val gattIface = GATT_CHARACTERISTIC1.value
        addMethodAt(
            path,
            "ReadValue",
            inSig = "a{sv}",
            outSig = "ay",
            code = "ret = self.props['$gattIface']['Value']"
        )
        addMethodAt(
            path,
            "WriteValue",
            inSig = "aya{sv}",
            outSig = "",
            code = "self.UpdateProperties('$gattIface', " +
                "{'Value': dbus.Array(args[0], signature='y')})"
        )
        addMethodAt(
            path,
            "StartNotify",
            inSig = "",
            outSig = "",
            code = "self.UpdateProperties('$gattIface', {'Notifying': dbus.Boolean(True)})"
        )
        addMethodAt(
            path,
            "StopNotify",
            inSig = "",
            outSig = "",
            code = "self.UpdateProperties('$gattIface', {'Notifying': dbus.Boolean(False)})"
        )
        return path
    }

    /** Flips the device's `ServicesResolved` (with PropertiesChanged), like bluetoothd does. */
    suspend fun resolveServices(devicePath: ObjectPath) {
        updatePropertiesAt(devicePath, DEVICE1, mapOf("ServicesResolved" to Variant(true)))
    }

    /**
     * Pushes a new characteristic [value] from the peer side — the BlueZ representation of an
     * incoming GATT notification (a `Value` PropertiesChanged on the characteristic).
     */
    suspend fun injectGattValue(charPath: ObjectPath, value: List<UByte>) {
        updatePropertiesAt(charPath, GATT_CHARACTERISTIC1, mapOf("Value" to Variant(value)))
    }

    private fun addGattObject(path: ObjectPath, iface: InterfaceName, props: Map<String, Variant>) {
        root.callMethod<Unit>(BLUEZ_MOCK, MethodName("AddGattObject")) {
            call(path.value, iface.value, props)
        }
    }

    /** Scripts a method on the object at [path] (on [GATT_CHARACTERISTIC1]). */
    private fun addMethodAt(
        path: ObjectPath,
        name: String,
        inSig: String,
        outSig: String,
        code: String
    ) {
        val control = createProxy(connection, ServiceName("org.bluez"), path)
        try {
            control.callMethod<Unit>(MOCK_INTERFACE, MethodName("AddMethod")) {
                call(GATT_CHARACTERISTIC1.value, name, inSig, outSig, code)
            }
        } finally {
            control.release()
        }
    }

    /** dbusmock control `UpdateProperties` on the object at [path] (emits PropertiesChanged). */
    private suspend fun updatePropertiesAt(
        path: ObjectPath,
        iface: InterfaceName,
        props: Map<String, Variant>
    ) = proxyAt(path) { control ->
        control.callMethod<Unit>(MOCK_INTERFACE, MethodName("UpdateProperties")) {
            call(iface.value, props)
        }
    }
}

/**
 * Collects `PropertiesChanged` signals from one object into a channel. The underlying
 * `onSignal` registration is active as soon as the constructor returns (no pumping needed) and
 * lives until the proxy is released.
 */
private class PropertyEventStream(proxy: Proxy) {
    private val events = Channel<Pair<InterfaceName, Map<PropertyName, Variant>>>(Channel.UNLIMITED)

    init {
        PropertiesProxy(proxy).registerPropertiesProxy { interfaceName, changed, _ ->
            events.trySend(interfaceName to changed)
        }
    }

    /**
     * Receives events until one carries [property] changed on [interfaceName], returning the
     * new value. Unrelated events (other properties/interfaces) are skipped, so sequential
     * awaits observe a property's changes in order.
     */
    suspend fun awaitChanged(interfaceName: InterfaceName, property: String): Variant =
        withTimeout(10_000) {
            var value: Variant? = null
            while (value == null) {
                val (iface, changed) = events.receive()
                if (iface == interfaceName) value = changed[PropertyName(property)]
            }
            value
        }
}

// --- Typed proxies (hand-written in the exact style the :codegen module emits) ------------------

private class Adapter1Proxy(val proxy: Proxy) {
    val addressProperty: PropertyDelegate<Adapter1Proxy, String> =
        proxy.propDelegate(ADAPTER1, PropertyName("Address"))

    val address: String by addressProperty

    val nameProperty: PropertyDelegate<Adapter1Proxy, String> =
        proxy.propDelegate(ADAPTER1, PropertyName("Name"))

    val name: String by nameProperty

    val poweredProperty: MutablePropertyDelegate<Adapter1Proxy, Boolean> =
        proxy.mutableDelegate(ADAPTER1, PropertyName("Powered"))

    var powered: Boolean by poweredProperty

    val discoveringProperty: PropertyDelegate<Adapter1Proxy, Boolean> =
        proxy.propDelegate(ADAPTER1, PropertyName("Discovering"))

    val discovering: Boolean by discoveringProperty

    suspend fun startDiscovery(): Unit =
        proxy.callMethodAsync(ADAPTER1, MethodName("StartDiscovery")) { call() }

    suspend fun stopDiscovery(): Unit =
        proxy.callMethodAsync(ADAPTER1, MethodName("StopDiscovery")) { call() }

    suspend fun setDiscoveryFilter(filter: Map<String, Variant>): Unit =
        proxy.callMethodAsync(ADAPTER1, MethodName("SetDiscoveryFilter")) { call(filter) }

    suspend fun removeDevice(device: ObjectPath): Unit =
        proxy.callMethodAsync(ADAPTER1, MethodName("RemoveDevice")) { call(device) }
}

private class Device1Proxy(val proxy: Proxy) {
    val addressProperty: PropertyDelegate<Device1Proxy, String> =
        proxy.propDelegate(DEVICE1, PropertyName("Address"))

    val address: String by addressProperty

    val aliasProperty: PropertyDelegate<Device1Proxy, String> =
        proxy.propDelegate(DEVICE1, PropertyName("Alias"))

    val alias: String by aliasProperty

    val pairedProperty: PropertyDelegate<Device1Proxy, Boolean> =
        proxy.propDelegate(DEVICE1, PropertyName("Paired"))

    val paired: Boolean by pairedProperty

    val connectedProperty: PropertyDelegate<Device1Proxy, Boolean> =
        proxy.propDelegate(DEVICE1, PropertyName("Connected"))

    val connected: Boolean by connectedProperty

    val rssiProperty: PropertyDelegate<Device1Proxy, Short> =
        proxy.propDelegate(DEVICE1, PropertyName("RSSI"))

    val rssi: Short by rssiProperty

    val adapterProperty: PropertyDelegate<Device1Proxy, ObjectPath> =
        proxy.propDelegate(DEVICE1, PropertyName("Adapter"))

    val adapter: ObjectPath by adapterProperty

    val servicesResolvedProperty: PropertyDelegate<Device1Proxy, Boolean> =
        proxy.propDelegate(DEVICE1, PropertyName("ServicesResolved"))

    val servicesResolved: Boolean by servicesResolvedProperty

    val uuidsProperty: PropertyDelegate<Device1Proxy, List<String>> =
        proxy.propDelegate(DEVICE1, PropertyName("UUIDs"))

    val uuids: List<String> by uuidsProperty

    suspend fun connect(): Unit = proxy.callMethodAsync(DEVICE1, MethodName("Connect")) { call() }

    suspend fun disconnect(): Unit =
        proxy.callMethodAsync(DEVICE1, MethodName("Disconnect")) { call() }

    suspend fun pair(): Unit = proxy.callMethodAsync(DEVICE1, MethodName("Pair")) { call() }
}

private class GattCharacteristic1Proxy(val proxy: Proxy) {
    val uuidProperty: PropertyDelegate<GattCharacteristic1Proxy, String> =
        proxy.propDelegate(GATT_CHARACTERISTIC1, PropertyName("UUID"))

    val uuid: String by uuidProperty

    val valueProperty: PropertyDelegate<GattCharacteristic1Proxy, List<UByte>> =
        proxy.propDelegate(GATT_CHARACTERISTIC1, PropertyName("Value"))

    val value: List<UByte> by valueProperty

    val notifyingProperty: PropertyDelegate<GattCharacteristic1Proxy, Boolean> =
        proxy.propDelegate(GATT_CHARACTERISTIC1, PropertyName("Notifying"))

    val notifying: Boolean by notifyingProperty

    suspend fun readValue(options: Map<String, Variant>): List<UByte> =
        proxy.callMethodAsync(GATT_CHARACTERISTIC1, MethodName("ReadValue")) { call(options) }

    suspend fun writeValue(value: List<UByte>, options: Map<String, Variant>): Unit =
        proxy.callMethodAsync(GATT_CHARACTERISTIC1, MethodName("WriteValue")) {
            call(value, options)
        }

    suspend fun startNotify(): Unit =
        proxy.callMethodAsync(GATT_CHARACTERISTIC1, MethodName("StartNotify")) { call() }

    suspend fun stopNotify(): Unit =
        proxy.callMethodAsync(GATT_CHARACTERISTIC1, MethodName("StopNotify")) { call() }
}
