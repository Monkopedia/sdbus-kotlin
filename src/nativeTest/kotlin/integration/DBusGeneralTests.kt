@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.Message
import com.monkopedia.sdbus.header.createBusConnection
import com.monkopedia.sdbus.header.return_slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import platform.posix.size_t
import platform.posix.sync

class CppEventLoop : DBusGeneralTests() {
}

class AdaptorAndProxy {
    @Test
    fun CanBeConstructedSuccessfully(): Unit = memScoped {
        val connection = createBusConnection().own(this)
        connection.requestName(SERVICE_NAME);

        val adaptor = TestAdaptor(this, connection, OBJECT_PATH);
        val proxy = TestProxy(this, SERVICE_NAME, OBJECT_PATH);

        connection.releaseName(SERVICE_NAME);
    }
}

abstract class DBusGeneralTests : BaseTest() {
    private val fixture: ConnectionTestFixture = TestFixtureSdBusCppLoop(this)
//    using ADirectConnection = TestFixtureWithDirectConnection;

    /*-------------------------------------*/
    /* --          TEST CASES           -- */
    /*-------------------------------------*/

    @Test
    fun `AConnection WillCallCallbackHandlerForIncomingMessageMatchingMatchRule`(): Unit =
        memScoped {
            val matchRule = "sender='$SERVICE_NAME',path='$OBJECT_PATH'";
            var matchingMessageReceived = atomic(false)
            s_proxyConnection.addMatch(matchRule, { msg ->
                if (msg.getPath() == OBJECT_PATH.value) {
                    matchingMessageReceived.value = true;
                }
            }, return_slot).own(this)

            fixture.m_adaptor?.emitSimpleSignal();

            assertTrue(waitUntil(matchingMessageReceived));
        }

    @Test
    fun `AConnection CanInstallMatchRuleAsynchronously`(): Unit = memScoped {
        val matchRule = "sender='${SERVICE_NAME.value}',path='${OBJECT_PATH.value}'"
        val matchingMessageReceived = atomic(false);
        val matchRuleInstalled = atomic(false);
        s_proxyConnection.addMatchAsync(matchRule, { msg ->
            if (msg.getPath() == OBJECT_PATH.value) {
                matchingMessageReceived.value = true;
            }
        }, {
            matchRuleInstalled.value = true;
        }, return_slot).own(this)

        assertTrue(waitUntil(matchRuleInstalled));

        fixture.m_adaptor?.emitSimpleSignal();

        assertTrue(waitUntil(matchingMessageReceived));
    }

    @Test
    fun `AConnection WillUnsubscribeMatchRuleWhenClientDestroysTheAssociatedSlot`(): Unit {
        val matchRule = "sender='${SERVICE_NAME.value}',path='${OBJECT_PATH.value}'";
        val matchingMessageReceived = atomic(false);
        memScoped {
            s_proxyConnection.addMatch(matchRule, { msg ->
                if (msg.getPath() == OBJECT_PATH.value) matchingMessageReceived.value = true
            }, return_slot).own(this)
        }

        fixture.m_adaptor?.emitSimpleSignal();

        assertFalse(waitUntil(matchingMessageReceived, 1.seconds));
    }

    @Test
    fun `AConnection CanAddFloatingMatchRule`(): Unit {
        val matchRule = "sender='${SERVICE_NAME.value}',path='${OBJECT_PATH.value}'";
        val matchingMessageReceived = atomic(false)
        memScoped {
            val con = createBusConnection().own(this)
            con.enterEventLoopAsync();
            val callback = { msg: Message ->
                if (msg.getPath() == OBJECT_PATH.value)
                    matchingMessageReceived.value = true;
            };
            con.addMatch(matchRule, callback);
            fixture.m_adaptor?.emitSimpleSignal();
            assertTrue(waitUntil(matchingMessageReceived, 2.seconds));
            matchingMessageReceived.value = false;
        }
        fixture.m_adaptor?.emitSimpleSignal();

        assertFalse(waitUntil(matchingMessageReceived, 1.seconds));
    }

    @Test
    fun `AConnection WillNotPassToMatchCallbackMessagesThatDoNotMatchTheRule`(): Unit = memScoped {
        val matchRule = "type='signal',interface='${INTERFACE_NAME.value}',member='simpleSignal'";
        val numberOfMatchingMessages = atomic(0.convert<size_t>());
        s_proxyConnection.addMatch(matchRule, { msg ->
            if (msg.getMemberName() == "simpleSignal") {
                numberOfMatchingMessages.value++;
            }
        }, return_slot).own(this)
        val adaptor2 = TestAdaptor(this, s_adaptorConnection, OBJECT_PATH_2);

        fixture.m_adaptor?.emitSignalWithMap(emptyMap());
        adaptor2.emitSimpleSignal();
        fixture.m_adaptor?.emitSimpleSignal();

        assertTrue(waitUntil({ numberOfMatchingMessages.value == 2u.convert<size_t>() }))
        assertFalse(waitUntil({ numberOfMatchingMessages.value > 2u }, 1.seconds))
    }

    // A simple direct connection test similar in nature to https://github.com/systemd/systemd/blob/main/src/libsystemd/sd-bus/test-bus-server.c
    @Test fun `AConnection CanBeUsedBetweenClientAndServer`(): Unit = memScoped {
        val v = fixture.m_proxy!!.sumArrayItems(listOf(1u.toUShort(), 7u.toUShort()), arrayOf(2u, 3u, 4u));
        fixture.m_adaptor?.emitSimpleSignal();

        // Make sure method call passes and emitted signal is received
        assertEquals(1u + 7u + 2u + 3u + 4u, v);
        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal));
    }
}

class DirectConnectionTest : BaseTest() {
    private val fixture = TextFixtureWithDirectConnection(this)

    // A simple direct connection test similar in nature to https://github.com/systemd/systemd/blob/main/src/libsystemd/sd-bus/test-bus-server.c
    @Test fun `ADirectConnection CanBeUsedBetweenClientAndServer`(): Unit = memScoped {
        val v = fixture.m_proxy!!.sumArrayItems(listOf(1u.toUShort(), 7u.toUShort()), arrayOf(2u, 3u, 4u));
        fixture.m_adaptor?.emitSimpleSignal();

        // Make sure method call passes and emitted signal is received
        assertEquals(1u + 7u + 2u + 3u + 4u, v);
        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal));
    }
}