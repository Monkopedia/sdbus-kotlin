package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.Connection
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.signal
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.fail

/**
 * JVM analogue of [com.monkopedia.sdbus.unit.CleanerLifecycleSoakTest] — proves a served object on the
 * owned (junixsocket) JVM backend is actually released, not leaked, after `release()`.
 *
 * The bug this guards: a served object's Properties Get/Set/GetAll dispatch handlers were registered
 * into the process-wide `JvmStaticDispatch` singleton and never removed on teardown. Each captures the
 * `WireDbusObject` (and through it the user adaptor's getters/setters and the wire connection), so any
 * object that declared a property leaked for the process lifetime. Pure-method objects were unaffected
 * — hence the adaptor here registers a property.
 *
 * Uses ordinary JVM GC (no Kotlin/Native cleaner), but it is still GC-timing dependent, so — like the
 * native lifecycle soak — it is excluded from the default `jvmTest` run behind the `-PgcSoak` gate and
 * runs only on the nightly schedule. Needs a real session bus (run under `dbus-run-session`); when no
 * bus is available the connection factory throws and the test treats that as a skip.
 */
class CleanerJvmLifecycleSoakTest {

    private val iface = InterfaceName("org.sdbuskotlin.jvmsoak.Iface")
    private val path = ObjectPath("/org/sdbuskotlin/jvmsoak")

    private class SoakAdaptor(connection: Connection, path: ObjectPath, iface: InterfaceName) {
        private val obj: Object = createObject(connection, path)
        private var value: ULong = 0u

        fun register(iface: InterfaceName) {
            // Intentionally discard the addVTable Resource: an object's own release() must clean up
            // everything it registered. This is exactly the path that leaked — obj.release() did not
            // remove the Properties dispatch handlers, so a property-bearing object leaked forever.
            obj.addVTable(iface) {
                method(MethodName("Ping")) {
                    acall(this@SoakAdaptor::ping)
                }
                prop(PropertyName("Value")) {
                    withGetter { value }
                }
                signal(SignalName("Tick")) {
                    with<ULong>("count")
                }
            }
        }

        @Suppress("RedundantSuspendModifier")
        suspend fun ping(): ULong = value++

        fun release() = obj.release()
    }

    @Test
    fun servedObjectWithPropertyIsCollectedAfterRelease() {
        val connection = connectOrNull() ?: return // no bus in this environment -> skip
        try {
            val weak = setUpAndRelease(connection)
            if (!collected(weak)) {
                fail(
                    "Served object with a property was NOT collected after release() — the " +
                        "JvmStaticDispatch singleton is still holding its Properties handlers " +
                        "(which capture the adaptor and the wire connection). A resource leak."
                )
            }
        } finally {
            connection.release()
        }
    }

    /** Frame returns a weak ref only; the adaptor is unreachable on return once released. */
    private fun setUpAndRelease(connection: Connection): WeakReference<Any> {
        val adaptor = SoakAdaptor(connection, path, iface)
        adaptor.register(iface)
        adaptor.release()
        return WeakReference(adaptor)
    }

    private fun collected(weak: WeakReference<Any>): Boolean {
        repeat(100) {
            System.gc()
            Runtime.getRuntime().runFinalization()
            Thread.sleep(20)
            if (weak.get() == null) return true
        }
        return false
    }

    // The connection factory throws when no usable bus is reachable (issue #81); treat as skip.
    private fun connectOrNull(): Connection? = try {
        createBusConnection()
    } catch (e: Error) {
        null
    }
}
