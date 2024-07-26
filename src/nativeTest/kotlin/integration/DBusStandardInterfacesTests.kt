/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sdbus-kotlin. If not, see <http://www.gnu.org/licenses/>.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectManagerProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertiesProxy.Companion.get
import com.monkopedia.sdbus.PropertiesProxy.Companion.getAsync
import com.monkopedia.sdbus.PropertiesProxy.Companion.set
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.onSignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import platform.posix.F_OK
import platform.posix.access

class DBusStandardInterfacesTests : BaseTest() {
    private val fixture = SdbusConnectionFixture(this)

    @Test
    fun pingsViaPeerInterface() {
        fixture.proxy!!.ping()
    }

    @Test
    fun answersMachineUuidViaPeerInterface() {
        if (access("/etc/machine-id", F_OK) == -1 &&
            access("/var/lib/dbus/machine-id", F_OK) == -1
        ) {
            println(
                "/etc/machine-id and /var/lib/dbus/machine-id files do not exist, " +
                    "GetMachineId() will not work"
            )
        }

        fixture.proxy!!.getMachineId()
    }

    @Test
    fun getsPropertyViaPropertiesInterface() {
        assertEquals(
            DEFAULT_STATE_VALUE,
            fixture.proxy!!.get(INTERFACE_NAME, PropertyName("state"))
        )
    }

    @Test
    fun getsPropertyAsynchronouslyViaPropertiesInterfaceWithFuture() = runTest {
        val future: String = fixture.proxy!!.getAsync(INTERFACE_NAME, PropertyName("state"))

        assertEquals(DEFAULT_STATE_VALUE, future)
    }

    @Test
    fun setsPropertyViaPropertiesInterface() {
        val newActionValue = 2345u

        fixture.proxy!!.set(INTERFACE_NAME, PropertyName("action"), newActionValue)

        assertEquals(newActionValue, fixture.proxy!!.action())
    }

    @Test
    fun setsPropertyAsynchronouslyViaPropertiesInterfaceWithFuture() = runTest {
        val newActionValue = 2347u

        fixture.proxy!!.setAsync(
            INTERFACE_NAME,
            PropertyName("action"),
            Variant(newActionValue)
        )

        assertEquals(newActionValue, fixture.proxy!!.action())
    }

    @Test
    fun getsAllPropertiesViaPropertiesInterface() {
        val properties = fixture.proxy!!.getAll(INTERFACE_NAME)

        assertEquals(3, properties.size)
        assertEquals(
            DEFAULT_STATE_VALUE,
            properties[STATE_PROPERTY]?.get<String>()
        )
        assertEquals(
            DEFAULT_ACTION_VALUE,
            properties[ACTION_PROPERTY]?.get<UInt>()
        )
        assertEquals(
            DEFAULT_BLOCKING_VALUE,
            properties[BLOCKING_PROPERTY]?.get<Boolean>()
        )
    }

    @Test
    fun getsAllPropertiesAsynchronouslyViaPropertiesInterfaceWithFuture() = runTest {
        val properties = fixture.proxy!!.getAllAsync(INTERFACE_NAME)

        assertEquals(3, properties.size)
        assertEquals(
            DEFAULT_STATE_VALUE,
            properties[STATE_PROPERTY]?.get<String>()
        )
        assertEquals(
            DEFAULT_ACTION_VALUE,
            properties[ACTION_PROPERTY]?.get<UInt>()
        )
        assertEquals(
            DEFAULT_BLOCKING_VALUE,
            properties[BLOCKING_PROPERTY]?.get<Boolean>()
        )
    }

    @Test
    fun emitsPropertyChangedSignalForSelectedProperties() {
        val signalReceived = atomic(false)
        fixture.proxy!!.propertiesChangedHandler = { interfaceName, changedProperties, _ ->
            assertEquals(INTERFACE_NAME, interfaceName)
            assertEquals(1, changedProperties.size)
            assertEquals(
                !DEFAULT_BLOCKING_VALUE,
                changedProperties[BLOCKING_PROPERTY]?.get<Boolean>()
            )
            signalReceived.value = true
        }

        fixture.proxy!!.blocking(!DEFAULT_BLOCKING_VALUE)
        fixture.proxy!!.action(DEFAULT_ACTION_VALUE * 2u)
        fixture.adaptor!!.obj.emitPropertiesChangedSignal(INTERFACE_NAME, listOf(BLOCKING_PROPERTY))

        assertTrue(waitUntil(signalReceived))
    }

    @Test
    fun emitsPropertyChangedSignalForAllProperties() {
        val signalReceived = atomic(false)
        fixture.proxy!!.propertiesChangedHandler =
            { interfaceName, changedProperties, invalidatedProperties ->
                assertEquals(INTERFACE_NAME, interfaceName)
                assertEquals(1, changedProperties.size)
                assertEquals(
                    DEFAULT_BLOCKING_VALUE,
                    changedProperties[BLOCKING_PROPERTY]?.get<Boolean>()
                )
                assertEquals(1, invalidatedProperties.size)
                assertEquals("action", invalidatedProperties[0].value)
                signalReceived.value = true
            }

        fixture.adaptor!!.obj.emitPropertiesChangedSignal(INTERFACE_NAME)

        assertTrue(waitUntil(signalReceived))
    }

    @Test
    fun getsZeroManagedObjectsIfHasNoSubPathObjects() {
        fixture.adaptor!!.obj.release()
        val objectsInterfacesAndProperties = fixture.objectManagerProxy!!.getManagedObjects()

        assertEquals(0, objectsInterfacesAndProperties.size)
    }

    @Test
    fun getsManagedObjectsSuccessfully() {
        val adaptor2 = TestAdaptor(globalAdaptorConnection, OBJECT_PATH_2)
        adaptor2.registerAdaptor()
        val objectsInterfacesAndProperties = fixture.objectManagerProxy!!.getManagedObjects()

        assertEquals(2, objectsInterfacesAndProperties.size)
        assertEquals(
            DEFAULT_ACTION_VALUE,
            objectsInterfacesAndProperties.get(OBJECT_PATH)
                ?.get(INTERFACE_NAME)
                ?.get(ACTION_PROPERTY)?.get<UInt>()
        )
        assertEquals(
            DEFAULT_ACTION_VALUE,
            objectsInterfacesAndProperties.get(OBJECT_PATH_2)
                ?.get(INTERFACE_NAME)
                ?.get(ACTION_PROPERTY)?.get<UInt>()
        )
    }

    @Test
    fun emitsInterfacesAddedSignalForSelectedObjectInterfaces(): Unit = runBlocking {
        val completableDeferred = CompletableDeferred<Result<*>>()
        launch {
            fixture.objectManagerProxy!!.objectData(OBJECT_PATH).first { it.isNotEmpty() }
                .let { interfacesAndProperties ->
                    completableDeferred.complete(
                        kotlin.runCatching {
                            assertEquals(1, interfacesAndProperties.size)
                            assertNotNull(interfacesAndProperties.get(INTERFACE_NAME))
                            assertEquals(2, interfacesAndProperties.get(INTERFACE_NAME)?.size)
                            assertNotNull(
                                interfacesAndProperties.get(INTERFACE_NAME)?.get(STATE_PROPERTY)
                            )
                            assertNotNull(
                                interfacesAndProperties.get(INTERFACE_NAME)?.get(BLOCKING_PROPERTY)
                            )
                        }
                    )
                }
        }

        fixture.adaptor!!.obj.emitInterfacesAddedSignal(listOf(INTERFACE_NAME))

        withTimeout(5.seconds) { completableDeferred.await() }.getOrThrow()
    }

    @Test
    fun emitsInterfacesAddedSignalForAllObjectInterfaces() = runTest {
        val signalReceived = atomic(false)
        launch(Dispatchers.IO) {
            fixture.objectManagerProxy!!.objectData(OBJECT_PATH).first { it.isNotEmpty() }
                .let { interfacesAndProperties ->

                    assertEquals(4, interfacesAndProperties.size)
                    assertNotNull(interfacesAndProperties.get(INTERFACE_NAME))
                    assertEquals(2, interfacesAndProperties.get(INTERFACE_NAME)?.size)
                    assertNotNull(interfacesAndProperties.get(INTERFACE_NAME)?.get(STATE_PROPERTY))
                    assertNotNull(
                        interfacesAndProperties.get(INTERFACE_NAME)?.get(BLOCKING_PROPERTY)
                    )

                    signalReceived.value = true
                }
        }

        fixture.adaptor!!.obj.emitInterfacesAddedSignal()

        assertTrue(waitUntil(signalReceived))
    }

    @Test
    fun emitsInterfacesRemovedSignalForSelectedObjectInterfaces() = runTest {
        val signalReceived = atomic(false)
        val readyToEmit = atomic(false)
        val ready = atomic(false)
        val job = launch(Dispatchers.IO) {
            fixture.objectManagerProxy!!.interfacesFor(OBJECT_PATH).onStart {
                readyToEmit.value = true
            }.collect {
                if (ready.value && INTERFACE_NAME !in it) {
                    signalReceived.value = true
                } else if (INTERFACE_NAME in it) {
                    ready.value = true
                }
            }
        }
        assertTrue(waitUntil(readyToEmit), "Ready to emit")
        fixture.adaptor!!.obj.emitInterfacesAddedSignal(listOf(INTERFACE_NAME))

        assertTrue(waitUntil(ready), "Ready for remove")

        fixture.adaptor!!.obj.emitInterfacesRemovedSignal(listOf(INTERFACE_NAME))

        assertTrue(waitUntil(signalReceived))
        job.cancel()
    }

    @Test
    fun emitsInterfacesRemovedSignalForAllObjectInterfaces() {
        val signalReceived = atomic(false)
        fixture.objectManagerProxy!!.proxy.onSignal(
            ObjectManagerProxy.INTERFACE_NAME,
            SignalName("InterfacesRemoved")
        ) {
            call { objectPath: ObjectPath, interfaces: List<InterfaceName> ->
                assertEquals(OBJECT_PATH, objectPath)
                assertEquals(4, interfaces.size); // INTERFACE_NAME + 3 standard interfaces
                signalReceived.value = true
            }
        }

        fixture.adaptor!!.obj.emitInterfacesRemovedSignal()

        assertTrue(waitUntil(signalReceived))
    }
}
