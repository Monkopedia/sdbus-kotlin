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
    private val fixture: ConnectionTestFixture = TestFixtureSdBusCppLoop(this)

    /*-------------------------------------*/
    /* --          TEST CASES           -- */
    /*-------------------------------------*/

    @Test
    fun willCallCallbackHandlerForIncomingMessageMatchingMatchRule() {
        val matchRule = "sender='$SERVICE_NAME',path='$OBJECT_PATH'"
        var matchingMessageReceived = atomic(false)
        val slot = s_proxyConnection.addMatch(matchRule) { msg: Message ->
            if (msg.getPath() == OBJECT_PATH.value) {
                matchingMessageReceived.value = true
            }
        }

        fixture.m_adaptor?.emitSimpleSignal()

        assertTrue(waitUntil(matchingMessageReceived))
        slot.release()
    }

    @Test
    fun canInstallMatchRuleAsynchronously() {
        val matchRule = "sender='${SERVICE_NAME.value}',path='${OBJECT_PATH.value}'"
        val matchingMessageReceived = atomic(false)
        val matchRuleInstalled = atomic(false)
        val slot = s_proxyConnection.addMatchAsync(matchRule, { msg: Message ->
            if (msg.getPath() == OBJECT_PATH.value) {
                matchingMessageReceived.value = true
            }
        }, {
            matchRuleInstalled.value = true
        })

        assertTrue(waitUntil(matchRuleInstalled))

        fixture.m_adaptor?.emitSimpleSignal()

        assertTrue(waitUntil(matchingMessageReceived))
        slot.release()
    }

    @Test
    fun willUnsubscribeMatchRuleWhenClientDestroysTheAssociatedSlot() {
        val matchRule = "sender='${SERVICE_NAME.value}',path='${OBJECT_PATH.value}'"
        val matchingMessageReceived = atomic(false)
        val slot = s_proxyConnection.addMatch(matchRule) { msg: Message ->
            if (msg.getPath() == OBJECT_PATH.value) matchingMessageReceived.value = true
        }
        slot.release()

        fixture.m_adaptor?.emitSimpleSignal()

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
        fixture.m_adaptor?.emitSimpleSignal()
        assertTrue(waitUntil(matchingMessageReceived, 2.seconds))
        matchingMessageReceived.value = false
        con.release()
        fixture.m_adaptor?.emitSimpleSignal()

        assertFalse(waitUntil(matchingMessageReceived, 1.seconds))
    }

    @Test
    fun willNotPassToMatchCallbackMessagesThatDoNotMatchTheRule() {
        val matchRule = "type='signal',interface='${INTERFACE_NAME.value}',member='simpleSignal'"
        val numberOfMatchingMessages = atomic(0.convert<size_t>())
        val slot = s_proxyConnection.addMatch(matchRule) { msg: Message ->
            if (msg.getMemberName() == "simpleSignal") {
                numberOfMatchingMessages.value++
            }
        }
        val adaptor2 = TestAdaptor(s_adaptorConnection, OBJECT_PATH_2)

        fixture.m_adaptor?.emitSignalWithMap(emptyMap())
        adaptor2.emitSimpleSignal()
        fixture.m_adaptor?.emitSimpleSignal()

        assertTrue(waitUntil({ numberOfMatchingMessages.value == 2u.convert<size_t>() }))
        assertFalse(waitUntil({ numberOfMatchingMessages.value > 2u }, 1.seconds))
        slot.release()
    }

    // A simple direct connection test similar in nature to https://github.com/systemd/systemd/blob/main/src/libsystemd/sd-bus/test-bus-server.c
    @Test
    fun canBeUsedBetweenClientAndServer() {
        val v = fixture.m_proxy!!.sumArrayItems(
            listOf(1u.toUShort(), 7u.toUShort()),
            arrayOf(2u, 3u, 4u)
        )
        fixture.m_adaptor?.emitSimpleSignal()

        // Make sure method call passes and emitted signal is received
        assertEquals(1u + 7u + 2u + 3u + 4u, v)
        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal))
    }
}

class DirectConnectionTest : BaseTest() {
    private val fixture = TextFixtureWithDirectConnection(this)

    // A simple direct connection test similar in nature to https://github.com/systemd/systemd/blob/main/src/libsystemd/sd-bus/test-bus-server.c
    @Test
    fun canBeUsedBetweenClientAndServer() {
        val v = fixture.m_proxy!!.sumArrayItems(
            listOf(1u.toUShort(), 7u.toUShort()),
            arrayOf(2u, 3u, 4u)
        )
        fixture.m_adaptor?.emitSimpleSignal()

        // Make sure method call passes and emitted signal is received
        assertEquals(1u + 7u + 2u + 3u + 4u, v)
        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal))
        println("Done with test")
    }
}
