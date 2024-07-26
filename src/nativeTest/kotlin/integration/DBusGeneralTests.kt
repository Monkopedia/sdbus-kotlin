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

import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.createBusConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import platform.posix.size_t

class AdaptorAndProxy {
    @Test
    fun canBeConstructedSuccessfully() {
        val connection = createBusConnection()
        connection.requestName(SERVICE_NAME)

        val adaptor = TestAdaptor(connection, OBJECT_PATH)
        val proxy = TestProxy(SERVICE_NAME, OBJECT_PATH)

        connection.releaseName(SERVICE_NAME)
        adaptor.obj.release()
        proxy.proxy.release()
    }
}

class CppEventLoop : BaseTest() {
    private val fixture = SdbusConnectionFixture(this)

    /*-------------------------------------*/
    /* --          TEST CASES           -- */
    /*-------------------------------------*/

    @Test
    fun willCallCallbackHandlerForIncomingMessageMatchingMatchRule() {
        val matchRule = "sender='$SERVICE_NAME',path='$OBJECT_PATH'"
        var matchingMessageReceived = atomic(false)
        val slot = globalProxyConnection.addMatch(matchRule) { msg: Message ->
            if (msg.getPath() == OBJECT_PATH.value) {
                matchingMessageReceived.value = true
            }
        }

        fixture.adaptor?.emitSimpleSignal()

        assertTrue(waitUntil(matchingMessageReceived))
        slot.release()
    }

    @Test
    fun canInstallMatchRuleAsynchronously() {
        val matchRule = "sender='${SERVICE_NAME.value}',path='${OBJECT_PATH.value}'"
        val matchingMessageReceived = atomic(false)
        val matchRuleInstalled = atomic(false)
        val slot = globalProxyConnection.addMatchAsync(matchRule, { msg: Message ->
            if (msg.getPath() == OBJECT_PATH.value) {
                matchingMessageReceived.value = true
            }
        }, {
            matchRuleInstalled.value = true
        })

        assertTrue(waitUntil(matchRuleInstalled))

        fixture.adaptor?.emitSimpleSignal()

        assertTrue(waitUntil(matchingMessageReceived))
        slot.release()
    }

    @Test
    fun willUnsubscribeMatchRuleWhenClientDestroysTheAssociatedSlot() {
        val matchRule = "sender='${SERVICE_NAME.value}',path='${OBJECT_PATH.value}'"
        val matchingMessageReceived = atomic(false)
        val slot = globalProxyConnection.addMatch(matchRule) { msg: Message ->
            if (msg.getPath() == OBJECT_PATH.value) matchingMessageReceived.value = true
        }
        slot.release()

        fixture.adaptor?.emitSimpleSignal()

        assertFalse(waitUntil(matchingMessageReceived, 1.seconds))
    }

    @Test
    fun canAddFloatingMatchRule() {
        val matchingMessageReceived = atomic(false)
        val matchRule = "sender='${SERVICE_NAME.value}',path='${OBJECT_PATH.value}'"
        val con = createBusConnection()
        con.enterEventLoopAsync()
        val callback = { msg: Message ->
            if (msg.getPath() == OBJECT_PATH.value) {
                matchingMessageReceived.value = true
            }
        }
        con.addMatch(matchRule, callback)
        fixture.adaptor?.emitSimpleSignal()
        assertTrue(waitUntil(matchingMessageReceived, 2.seconds))
        matchingMessageReceived.value = false
        con.release()
        fixture.adaptor?.emitSimpleSignal()

        assertFalse(waitUntil(matchingMessageReceived, 1.seconds))
    }

    @Test
    fun willNotPassToMatchCallbackMessagesThatDoNotMatchTheRule() {
        val matchRule = "type='signal',interface='${INTERFACE_NAME.value}',member='simpleSignal'"
        val numberOfMatchingMessages = atomic(0.convert<size_t>())
        val slot = globalProxyConnection.addMatch(matchRule) { msg: Message ->
            if (msg.getMemberName() == "simpleSignal") {
                numberOfMatchingMessages.value++
            }
        }
        val adaptor2 = TestAdaptor(globalAdaptorConnection, OBJECT_PATH_2)

        fixture.adaptor?.emitSignalWithMap(emptyMap())
        adaptor2.emitSimpleSignal()
        fixture.adaptor?.emitSimpleSignal()

        assertTrue(waitUntil({ numberOfMatchingMessages.value == 2u.convert<size_t>() }))
        assertFalse(waitUntil({ numberOfMatchingMessages.value > 2u }, 1.seconds))
        slot.release()
    }

    // A simple direct connection test similar in nature to https://github.com/systemd/systemd/blob/main/src/libsystemd/sd-bus/test-bus-server.c
    @Test
    fun canBeUsedBetweenClientAndServer() {
        val v = fixture.proxy!!.sumArrayItems(
            listOf(1u.toUShort(), 7u.toUShort()),
            arrayOf(2u, 3u, 4u)
        )
        fixture.adaptor?.emitSimpleSignal()

        // Make sure method call passes and emitted signal is received
        assertEquals(1u + 7u + 2u + 3u + 4u, v)
        assertTrue(waitUntil(fixture.proxy!!.gotSimpleSignal))
    }
}

class DirectConnectionTest : BaseTest() {
    private val fixture = DirectConnectionFixture(this)

    // A simple direct connection test similar in nature to https://github.com/systemd/systemd/blob/main/src/libsystemd/sd-bus/test-bus-server.c
    @Test
    fun canBeUsedBetweenClientAndServer() {
        val v = fixture.proxy!!.sumArrayItems(
            listOf(1u.toUShort(), 7u.toUShort()),
            arrayOf(2u, 3u, 4u)
        )
        fixture.adaptor?.emitSimpleSignal()

        // Make sure method call passes and emitted signal is received
        assertEquals(1u + 7u + 2u + 3u + 4u, v)
        assertTrue(waitUntil(fixture.proxy!!.gotSimpleSignal))
        println("Done with test")
    }
}
