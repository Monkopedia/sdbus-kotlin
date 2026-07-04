package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.signal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Parity regression (#141): releasing a [com.monkopedia.sdbus.Proxy] must stop the signal handlers
 * registered through it, on both backends. Native's ProxyImpl.release() tears down its floating
 * signal slots; the JVM WireDbusProxy.release() was a no-op, so handlers kept firing (and leaked)
 * after the proxy was released. Runs on both targets.
 */
class ProxyLifecycleParityTest {

    @Test
    fun proxyReleaseStopsSignalHandlers() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.prel$id")
        val path = ObjectPath("/com/monkopedia/sdbus/prel$id")
        val iface = InterfaceName("com.monkopedia.sdbus.prel$id.Iface")
        val signalName = SignalName("Tick")
        val fires = atomic(0)

        val server = createBusConnection(service)
        val client = createBusConnection()
        val obj = createObject(server, path)
        val reg = obj.addVTable(iface) {
            method(MethodName("Ping")) { call<Unit> { } }
            signal(signalName) { with<Int>("v") }
        }
        server.startEventLoop()
        client.startEventLoop()
        val proxy = createProxy(client, service, path)

        // Register a handler but intentionally KEEP NO reference to the returned resource — so this
        // asserts that proxy.release() ALONE stops delivery, not an explicit handler release.
        proxy.onSignal(iface, signalName) {
            call { _: Int -> fires.incrementAndGet() }
        }

        try {
            // 1) Prove the pipe works: emit until the handler is observed to fire.
            var emitted = 0
            while (fires.value == 0 && emitted < 50) {
                obj.emitSignal(iface, signalName) { call(1) }
                emitted++
                if (fires.value == 0) delay(20)
            }
            assertTrue(fires.value >= 1, "handler should fire before release")

            // 2) Release the proxy and keep emitting; native stops delivery, JVM used to keep firing.
            val countAtRelease = fires.value
            proxy.release()
            repeat(10) {
                obj.emitSignal(iface, signalName) { call(1) }
                delay(30)
            }
            assertEquals(
                countAtRelease,
                fires.value,
                "no signal must be delivered to a released proxy's handler"
            )
        } finally {
            reg.release()
            obj.release()
            client.stopEventLoop()
            server.stopEventLoop()
            client.release()
            server.release()
        }
    }
}
