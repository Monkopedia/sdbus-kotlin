package com.monkopedia.sdbus

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Acceptance test for issue #90 (approach A): an EXTERNAL process must be able to reach a
 * JVM-hosted server object.
 *
 * The proof drives the JVM-exported object with `busctl` -- a genuinely separate process. This
 * is essential: two sdbus-kotlin connections in the *same* JVM short-circuit through the global
 * in-process [com.monkopedia.sdbus.internal.jvmdbus.JvmStaticDispatch] table (single-candidate
 * fallback), so a same-process "cross-connection" call never actually serializes onto the wire
 * and cannot detect the gap. busctl has no such entry and must go through dbus-java's native
 * object export, which only serves the object once the approach-A bridge has exported it.
 *
 * Before the fix this fails: dbus-java answers external method/property/ObjectManager calls with
 * `UnknownObject`, so every busctl invocation exits non-zero. After the fix the object is
 * exported and busctl gets real values.
 */
class JvmCrossProcessExportTest {

    private data class BusctlResult(val exitCode: Int, val output: String)

    private fun busctlPath(): String? = listOf("/usr/bin/busctl", "/bin/busctl")
        .firstOrNull { java.io.File(it).canExecute() }

    private fun runBusctl(address: String, busctl: String, vararg args: String): BusctlResult {
        val command = listOf(busctl, "--address=$address", *args)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = ByteArrayOutputStream()
        val pump = thread(start = true, isDaemon = true) {
            process.inputStream.use { it.copyTo(output) }
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
        pump.join(1_000)
        return BusctlResult(process.exitValue(), output.toString(Charsets.UTF_8))
    }

    private fun connectOrNull(connect: () -> Connection): Connection? = try {
        connect()
    } catch (e: Error) {
        null
    }

    @Test
    fun externalBusctlClientReachesJvmExportedObject() {
        val address = System.getenv("DBUS_SESSION_BUS_ADDRESS") ?: return
        val busctl = busctlPath() ?: return

        val suffix = "x${System.nanoTime()}"
        val service = ServiceName("com.monkopedia.sdbus.export$suffix")
        val managerPath = ObjectPath("/com/monkopedia/sdbus/export$suffix")
        val childPath = ObjectPath("${managerPath.value}/child")
        val iface = InterfaceName("com.monkopedia.sdbus.export.$suffix.Iface")
        val concat = MethodName("Concat")
        val valueProp = PropertyName("Value")

        val server = connectOrNull { createSessionBusConnection(service) } ?: return
        server.startEventLoop()
        val managerRegistration = server.addObjectManager(managerPath)
        val obj = createObject(server, childPath)
        val registration = obj.addVTable(iface) {
            method(concat) {
                call { a: String, b: Int -> "$a:$b" }
            }
            prop(valueProp) {
                withGetter { 42 }
            }
        }

        try {
            // 1) External method call on a JVM-hosted object.
            val call = runBusctl(
                address,
                busctl,
                "call",
                service.value,
                childPath.value,
                iface.value,
                concat.value,
                "si",
                "hello",
                "7"
            )
            assertEquals(0, call.exitCode, "busctl call failed: ${call.output}")
            assertTrue(
                call.output.contains("hello:7"),
                "Unexpected method reply: ${call.output}"
            )

            // 2) External org.freedesktop.DBus.Properties.Get.
            val property = runBusctl(
                address,
                busctl,
                "get-property",
                service.value,
                childPath.value,
                iface.value,
                valueProp.value
            )
            assertEquals(0, property.exitCode, "busctl get-property failed: ${property.output}")
            assertTrue(
                property.output.contains("42"),
                "Unexpected property reply: ${property.output}"
            )

            // 3) External org.freedesktop.DBus.ObjectManager.GetManagedObjects.
            val managed = runBusctl(
                address,
                busctl,
                "call",
                service.value,
                managerPath.value,
                "org.freedesktop.DBus.ObjectManager",
                "GetManagedObjects"
            )
            assertEquals(0, managed.exitCode, "busctl GetManagedObjects failed: ${managed.output}")
            assertTrue(
                managed.output.contains(childPath.value) && managed.output.contains("Value"),
                "Unexpected GetManagedObjects reply: ${managed.output}"
            )
        } finally {
            registration.release()
            obj.release()
            managerRegistration.release()
            server.release()
        }
    }
}
