package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PeerProxy
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.method
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Parity regression (#141): the standard interfaces org.freedesktop.DBus.Peer (Ping / GetMachineId)
 * and org.freedesktop.DBus.Introspectable (Introspect) must be answered for a SAME-PROCESS peer on
 * both backends. Native serves them via sd-bus regardless of same- vs cross-process; the JVM backend
 * served them on the incoming-wire path but the same-process local short-circuit threw UnknownMethod.
 * Runs on both targets.
 */
class StandardInterfaceParityTest {

    @Test
    fun sameProcessPeerAndIntrospectAreServed() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.std$id")
        val path = ObjectPath("/com/monkopedia/sdbus/std$id")
        val iface = InterfaceName("com.monkopedia.sdbus.std$id.Iface")

        val server = createBusConnection(service)
        val client = createBusConnection()
        val obj = createObject(server, path)
        val reg = obj.addVTable(iface) {
            method(MethodName("Ping")) { call<Unit> { } }
        }
        server.startEventLoop()
        val proxy = createProxy(client, service, path, runEventLoopThread = false)

        try {
            // Peer.Ping: must not throw (native answers directly; JVM used to 404 same-process).
            PeerProxy(proxy).ping()

            // Peer.GetMachineId: a non-empty machine id.
            val machineId = PeerProxy(proxy).getMachineId()
            assertTrue(machineId.isNotEmpty(), "machine id should be non-empty")

            // Introspectable.Introspect: XML that mentions the served interface.
            val xml = proxy.callMethod<String>(
                InterfaceName("org.freedesktop.DBus.Introspectable"),
                MethodName("Introspect")
            ) { }
            assertTrue(
                xml.contains(iface.value),
                "introspection XML should list the served interface, got: ${xml.take(200)}"
            )
        } finally {
            reg.release()
            proxy.release()
            obj.release()
            runBlocking { server.stopEventLoop() }
            client.release()
            server.release()
        }
    }
}
