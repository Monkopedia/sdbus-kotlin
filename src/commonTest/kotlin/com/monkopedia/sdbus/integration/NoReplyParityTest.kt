package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
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
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Parity regression (#141): a dontExpectReply (fire-and-forget) call must NOT wait for or require a
 * reply and must NOT throw for a missing target, on both backends. Native does sd_bus_send with no
 * reply; the JVM backend used to ignore the flag (wait up to 30s for a reply) and threw
 * UnknownMethod for a missing member. Runs on both targets.
 */
class NoReplyParityTest {

    @Test
    fun dontExpectReplyDeliversWithoutWaitingOrThrowing() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.nr$id")
        val path = ObjectPath("/com/monkopedia/sdbus/nr$id")
        val iface = InterfaceName("com.monkopedia.sdbus.nr$id.Iface")
        val hits = atomic(0)

        val server = createBusConnection(service)
        val client = createBusConnection()
        val obj = createObject(server, path)
        val reg = obj.addVTable(iface) {
            method(MethodName("Bump")) {
                call { _: Int ->
                    hits.incrementAndGet()
                    Unit
                }
            }
        }
        server.startEventLoop()
        val proxy = createProxy(client, service, path)

        try {
            // 1) A no-reply call to an existing method delivers the side effect and returns promptly
            // (must not block ~30s waiting for a reply).
            withTimeout(5_000) {
                proxy.callMethod<Unit>(iface, MethodName("Bump")) {
                    call(1)
                    dontExpectReply = true
                }
            }
            withTimeout(2_000) { while (hits.value == 0) delay(20) }
            assertTrue(hits.value >= 1, "no-reply call should still deliver the side effect")

            // 2) A no-reply call to a MISSING member must not throw (native fire-and-forgets).
            withTimeout(5_000) {
                proxy.callMethod<Unit>(iface, MethodName("NoSuchMethod")) {
                    call(1)
                    dontExpectReply = true
                }
            }
        } finally {
            reg.release()
            proxy.release()
            obj.release()
            client.stopEventLoop()
            server.stopEventLoop()
            client.release()
            server.release()
        }
    }
}
