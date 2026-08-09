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
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Proves an object exported by a JVM sdbus-kotlin service on the OWNED wire backend is reachable by
 * a genuinely EXTERNAL process — `busctl` (epic #93 phase 4, closes #90). busctl speaks the real
 * D-Bus wire protocol over its own socket, so it cannot be short-circuited by the in-process
 * JvmStaticDispatch the #90 investigation warned about: only true over-the-wire serving makes these
 * pass.
 *
 *  - before phase 4: the read loop ignored incoming METHOD_CALLs, so busctl got `UnknownObject`
 *    (or timed out) and every assertion here FAILED;
 *  - after phase 4: the calls are routed to the vtable handlers and replied over the wire, so
 *    busctl gets correct results.
 *
 * The owned wire connection is now the only JVM backend (epic #93 phase 6 retired dbus-java), so
 * this always runs. `busctl` is genuinely optional, so its absence is a real skip — unless
 * [EXTERNAL_TOOLS_REQUIRED_ENV] is set, as the CI job that guarantees busctl does, because there a
 * skip would silently delete this coverage (issue #229). A missing session bus is never optional —
 * every job that runs this wraps the build in `dbus-run-session` — so that FAILS. Neither is an
 * early return, which JUnit records as a pass (issue #227).
 */
class WireServeExternalTest {

    @Test
    fun externalBusctl_reachesServedObject() = runBlocking {
        val sessionBus = checkNotNull(System.getenv("DBUS_SESSION_BUS_ADDRESS")) {
            "DBUS_SESSION_BUS_ADDRESS is unset: this test serves an object on the session bus " +
                "and hands its address to busctl. Run the suite under " +
                "`dbus-run-session -- ./gradlew …`."
        }
        val busctl = requireExternalTool(
            "busctl",
            "no external peer can call the served object"
        )

        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.serve$id")
        val managerPath = ObjectPath("/com/monkopedia/sdbus/serve$id")
        val path = ObjectPath("/com/monkopedia/sdbus/serve$id/obj")
        val iface = InterfaceName("com.monkopedia.sdbus.serve$id.Interface")

        var prefix = "Hello"

        val connection = createBusConnection(service)
        connection.startEventLoop()
        val managerRegistration = connection.addObjectManager(managerPath)
        val obj = createObject(connection, path)
        val registration = obj.addVTable(iface) {
            // Sync handler.
            method(MethodName("Add")) {
                inputParamNames = listOf("a", "b")
                outputParamNames = listOf("sum")
                call { a: Int, b: Int -> a + b }
            }
            // Async (asyncCall) handler — completes off the caller's thread.
            method(MethodName("Concat")) {
                inputParamNames = listOf("a", "b")
                outputParamNames = listOf("joined")
                asyncCall { a: String, b: String -> a + b }
            }
            prop(PropertyName("Prefix")) {
                withGetter { prefix }
                withSetter<String> { prefix = it }
            }
        }

        fun busctl(vararg args: String): Pair<Int, String> {
            val process = ProcessBuilder(
                listOf(busctl.absolutePath, "--address=$sessionBus") + args
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("busctl ${args.joinToString(" ")} timed out\n$output")
            }
            return process.exitValue() to output
        }

        try {
            // 1. Method call (sync handler): Add(2, 3) -> 5.
            val (addCode, addOut) = busctl(
                "call",
                service.value,
                path.value,
                iface.value,
                "Add",
                "ii",
                "2",
                "3"
            )
            assertEquals(
                0,
                addCode,
                "busctl call Add failed (UnknownObject before phase 4?): $addOut"
            )
            assertTrue("5" in addOut, "Add reply missing expected sum: $addOut")

            // 2. Method call (async asyncCall handler): Concat("foo", "bar") -> "foobar".
            val (concatCode, concatOut) = busctl(
                "call",
                service.value,
                path.value,
                iface.value,
                "Concat",
                "ss",
                "foo",
                "bar"
            )
            assertEquals(0, concatCode, "busctl call Concat failed: $concatOut")
            assertTrue("foobar" in concatOut, "Concat reply missing expected value: $concatOut")

            // 3. Properties.Get -> initial "Hello".
            val (getCode, getOut) = busctl(
                "get-property",
                service.value,
                path.value,
                iface.value,
                "Prefix"
            )
            assertEquals(0, getCode, "busctl get-property failed: $getOut")
            assertTrue("Hello" in getOut, "Get(Prefix) missing initial value: $getOut")

            // 4. Properties.Set -> "World", then Get reflects it.
            val (setCode, setOut) = busctl(
                "set-property",
                service.value,
                path.value,
                iface.value,
                "Prefix",
                "s",
                "World"
            )
            assertEquals(0, setCode, "busctl set-property failed: $setOut")
            assertEquals("World", prefix, "setter did not run over the wire")
            val (getOut2Code, getOut2) = busctl(
                "get-property",
                service.value,
                path.value,
                iface.value,
                "Prefix"
            )
            assertEquals(0, getOut2Code, "busctl get-property (post-set) failed: $getOut2")
            assertTrue("World" in getOut2, "Get(Prefix) did not reflect Set: $getOut2")

            // 5. Properties.GetAll over the wire.
            val (getAllCode, getAllOut) = busctl(
                "call",
                service.value,
                path.value,
                "org.freedesktop.DBus.Properties",
                "GetAll",
                "s",
                iface.value
            )
            assertEquals(0, getAllCode, "busctl GetAll failed: $getAllOut")
            assertTrue("Prefix" in getAllOut, "GetAll missing Prefix: $getAllOut")

            // 6. Introspect the served object.
            val (introCode, introOut) = busctl("introspect", service.value, path.value)
            assertEquals(0, introCode, "busctl introspect failed: $introOut")
            assertTrue(iface.value in introOut, "Introspect missing our interface: $introOut")
            assertTrue("Add" in introOut, "Introspect missing Add method: $introOut")
            assertTrue("Prefix" in introOut, "Introspect missing Prefix property: $introOut")

            // 7. ObjectManager.GetManagedObjects on the manager path lists the child object.
            val (gmoCode, gmoOut) = busctl(
                "call",
                service.value,
                managerPath.value,
                "org.freedesktop.DBus.ObjectManager",
                "GetManagedObjects"
            )
            assertEquals(0, gmoCode, "busctl GetManagedObjects failed: $gmoOut")
            assertTrue(path.value in gmoOut, "GetManagedObjects missing the child object: $gmoOut")
            assertTrue(iface.value in gmoOut, "GetManagedObjects missing our interface: $gmoOut")
        } finally {
            registration.release()
            obj.release()
            managerRegistration.release()
            connection.stopEventLoop()
            connection.release()
        }
    }
}
