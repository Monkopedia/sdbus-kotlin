@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.PropertiesProxy.Companion.Get
import com.monkopedia.sdbus.header.PropertiesProxy.Companion.GetAsync
import com.monkopedia.sdbus.header.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import platform.posix.F_OK
import platform.posix.access

class DBusStandardInterfacesTests : BaseTest() {
    private val fixture = TestFixtureSdBusCppLoop(this)

    @Test
    fun pingsViaPeerInterface() {
        fixture.m_proxy!!.Ping()
    }

    @Test
    fun answersMachineUuidViaPeerInterface() {
        if (access("/etc/machine-id", F_OK) == -1 &&
            access("/var/lib/dbus/machine-id", F_OK) == -1
        ) {
            println(
                "/etc/machine-id and /var/lib/dbus/machine-id files do not exist, GetMachineId() will not work"
            )
        }

        fixture.m_proxy!!.GetMachineId()
    }

    @Test
    fun getsPropertyViaPropertiesInterface() {
        assertEquals(
            DEFAULT_STATE_VALUE,
            fixture.m_proxy!!.Get(INTERFACE_NAME.value, "state")
        )
    }

    @Test
    fun getsPropertyAsynchronouslyViaPropertiesInterfaceWithFuture() = runTest {
        val future: String = fixture.m_proxy!!.GetAsync(INTERFACE_NAME.value, "state")

        assertEquals(DEFAULT_STATE_VALUE, future)
    }

    @Test
    fun setsPropertyViaPropertiesInterface() {
        val newActionValue = 2345u

        fixture.m_proxy!!.Set(INTERFACE_NAME.value, "action", Variant(newActionValue))

        assertEquals(newActionValue, fixture.m_proxy!!.action())
    }

    @Test
    fun setsPropertyAsynchronouslyViaPropertiesInterfaceWithFuture() = runTest {
        val newActionValue = 2347u

        fixture.m_proxy!!.SetAsync(
            INTERFACE_NAME.value,
            "action",
            Variant(newActionValue)
        )

        assertEquals(newActionValue, fixture.m_proxy!!.action())
    }

    @Test
    fun getsAllPropertiesViaPropertiesInterface() {
        val properties = fixture.m_proxy!!.GetAll(INTERFACE_NAME)

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
        val properties = fixture.m_proxy!!.GetAllAsync(INTERFACE_NAME)

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
        fixture.m_proxy!!.m_onPropertiesChangedHandler = { interfaceName, changedProperties, _ ->
            assertEquals(INTERFACE_NAME, interfaceName)
            assertEquals(1, changedProperties.size)
            assertEquals(
                !DEFAULT_BLOCKING_VALUE,
                changedProperties[BLOCKING_PROPERTY]?.get<Boolean>()
            )
            signalReceived.value = true
        }

        fixture.m_proxy!!.blocking(!DEFAULT_BLOCKING_VALUE)
        fixture.m_proxy!!.action(DEFAULT_ACTION_VALUE * 2u)
        fixture.m_adaptor!!.emitPropertiesChangedSignal(INTERFACE_NAME, listOf(BLOCKING_PROPERTY))

        assertTrue(waitUntil(signalReceived))
    }

    @Test
    fun emitsPropertyChangedSignalForAllProperties() {
        val signalReceived = atomic(false)
        fixture.m_proxy!!.m_onPropertiesChangedHandler =
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

        fixture.m_adaptor!!.emitPropertiesChangedSignal(INTERFACE_NAME)

        assertTrue(waitUntil(signalReceived))
    }

    @Test
    fun getsZeroManagedObjectsIfHasNoSubPathObjects() {
        fixture.m_adaptor!!.m_object.release()
        val objectsInterfacesAndProperties = fixture.m_objectManagerProxy!!.getManagedObjects()

        assertEquals(0, objectsInterfacesAndProperties.size)
    }

    @Test
    fun getsManagedObjectsSuccessfully() {
        val adaptor2 = TestAdaptor(s_adaptorConnection, OBJECT_PATH_2)
        adaptor2.registerAdaptor()
        val objectsInterfacesAndProperties = fixture.m_objectManagerProxy!!.getManagedObjects()

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
        fixture.m_objectManagerProxy!!.m_onInterfacesAddedHandler =
            { objectPath, interfacesAndProperties ->
                completableDeferred.complete(
                    kotlin.runCatching {
                        assertEquals(OBJECT_PATH, objectPath)
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

        fixture.m_adaptor!!.emitInterfacesAddedSignal(listOf(INTERFACE_NAME))

        withTimeout(5.seconds) { completableDeferred.await() }.getOrThrow()
    }

    @Test
    fun emitsInterfacesAddedSignalForAllObjectInterfaces() {
        val signalReceived = atomic(false)
        fixture.m_objectManagerProxy!!.m_onInterfacesAddedHandler =
            { objectPath, interfacesAndProperties ->
                assertEquals(OBJECT_PATH, objectPath)

                assertEquals(4, interfacesAndProperties.size)
                assertNotNull(interfacesAndProperties.get(INTERFACE_NAME))
                assertEquals(2, interfacesAndProperties.get(INTERFACE_NAME)?.size)
                assertNotNull(interfacesAndProperties.get(INTERFACE_NAME)?.get(STATE_PROPERTY))
                assertNotNull(interfacesAndProperties.get(INTERFACE_NAME)?.get(BLOCKING_PROPERTY))

                signalReceived.value = true
            }

        fixture.m_adaptor!!.emitInterfacesAddedSignal()

        assertTrue(waitUntil(signalReceived))
    }

    @Test
    fun emitsInterfacesRemovedSignalForSelectedObjectInterfaces() {
        val signalReceived = atomic(false)
        fixture.m_objectManagerProxy!!.m_onInterfacesRemovedHandler = { objectPath, interfaces ->
            assertEquals(OBJECT_PATH, objectPath)
            assertEquals(1, interfaces.size)
            assertEquals(INTERFACE_NAME, interfaces[0])
            signalReceived.value = true
        }

        fixture.m_adaptor!!.emitInterfacesRemovedSignal(listOf(INTERFACE_NAME))

        assertTrue(waitUntil(signalReceived))
    }

    @Test
    fun emitsInterfacesRemovedSignalForAllObjectInterfaces() {
        val signalReceived = atomic(false)
        fixture.m_objectManagerProxy!!.m_onInterfacesRemovedHandler = { objectPath, interfaces ->
            assertEquals(OBJECT_PATH, objectPath)
            assertEquals(4, interfaces.size); // INTERFACE_NAME + 3 standard interfaces
            signalReceived.value = true
        }

        fixture.m_adaptor!!.emitInterfacesRemovedSignal()

        assertTrue(waitUntil(signalReceived))
    }
}
