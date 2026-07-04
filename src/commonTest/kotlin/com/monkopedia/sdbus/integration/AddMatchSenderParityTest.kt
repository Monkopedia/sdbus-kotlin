package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.emitSignal
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.signal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Parity regression (#141): Connection.addMatch with a rule specifying a well-known sender= must
 * receive matching signals on both backends. The daemon resolves the well-known name to its unique
 * owner and delivers frames stamped with that unique name; the JVM backend's local re-filter used
 * to compare the unique sender against the well-known name (never resolving the owner) and dropped
 * every signal. Runs on both targets.
 */
class AddMatchSenderParityTest {

    @Test
    fun addMatchWithWellKnownSenderReceivesSignals() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.am$id")
        val path = ObjectPath("/com/monkopedia/sdbus/am$id")
        val iface = InterfaceName("com.monkopedia.sdbus.am$id.Iface")
        val signalName = SignalName("Tick")

        val server = createBusConnection(service)
        val client = createBusConnection()
        val obj = createObject(server, path)
        val reg = obj.addVTable(iface) {
            method(MethodName("Ping")) { call<Unit> { } }
            signal(signalName) { with<Int>("v") }
        }
        server.startEventLoop()
        client.startEventLoop()

        val fired = CompletableDeferred<Unit>()
        val rule = "type='signal',sender='${service.value}'," +
            "interface='${iface.value}',member='${signalName.value}'"
        val matchReg = client.addMatch(rule) {
            if (!fired.isCompleted) fired.complete(Unit)
        }

        try {
            var emitted = 0
            while (!fired.isCompleted && emitted < 50) {
                obj.emitSignal(iface, signalName) { call(1) }
                emitted++
                if (!fired.isCompleted) delay(20)
            }
            assertNotNull(
                withTimeoutOrNull(2_000) { fired.await() },
                "addMatch with a well-known sender= should deliver the matching signal"
            )
        } finally {
            matchReg.release()
            reg.release()
            obj.release()
            client.stopEventLoop()
            server.stopEventLoop()
            client.release()
            server.release()
        }
    }
}
