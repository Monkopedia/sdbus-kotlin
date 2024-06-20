@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.Message
import com.monkopedia.sdbus.header.createBusConnection
import com.monkopedia.sdbus.header.return_slot
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
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
import platform.posix.usleep


class AdaptorAndProxy {
    @Test
    fun canBeConstructedSuccessfully(): Unit {
        val connection = createBusConnection()
        connection.requestName(SERVICE_NAME);

        val adaptor = TestAdaptor(connection, OBJECT_PATH);
        val proxy = TestProxy(SERVICE_NAME, OBJECT_PATH);

        connection.releaseName(SERVICE_NAME);
        adaptor.m_object.unregister()
        proxy.m_proxy.unregister()
    }
}

class CppEventLoop : BaseTest() {
    private val fixture: ConnectionTestFixture = TestFixtureSdBusCppLoop(this)
//    using ADirectConnection = TestFixtureWithDirectConnection;

    /*-------------------------------------*/
    /* --          TEST CASES           -- */
    /*-------------------------------------*/

    @Test
    fun willCallCallbackHandlerForIncomingMessageMatchingMatchRule(): Unit =
        memScoped {
            val matchRule = "sender='$SERVICE_NAME',path='$OBJECT_PATH'";
            var matchingMessageReceived = atomic(false)
            val slot = s_proxyConnection.addMatch(matchRule, { msg: Message ->
                if (msg.getPath() == OBJECT_PATH.value) {
                    matchingMessageReceived.value = true;
                }
            }, return_slot)

            fixture.m_adaptor?.emitSimpleSignal();

            assertTrue(waitUntil(matchingMessageReceived));
        }

    @Test
    fun canInstallMatchRuleAsynchronously(): Unit = memScoped {
        val matchRule = "sender='${SERVICE_NAME.value}',path='${OBJECT_PATH.value}'"
        val matchingMessageReceived = atomic(false);
        val matchRuleInstalled = atomic(false);
        val slot = s_proxyConnection.addMatchAsync(matchRule, { msg: Message ->
            if (msg.getPath() == OBJECT_PATH.value) {
                matchingMessageReceived.value = true;
            }
        }, {
            matchRuleInstalled.value = true;
        }, return_slot)

        assertTrue(waitUntil(matchRuleInstalled));

        fixture.m_adaptor?.emitSimpleSignal();

        assertTrue(waitUntil(matchingMessageReceived));
    }

    @OptIn(NativeRuntimeApi::class)
    @Test
    fun willUnsubscribeMatchRuleWhenClientDestroysTheAssociatedSlot(): Unit {
        val matchRule = "sender='${SERVICE_NAME.value}',path='${OBJECT_PATH.value}'";
        val matchingMessageReceived = atomic(false);
        {
            val slot = s_proxyConnection.addMatch(matchRule, { msg: Message ->
                if (msg.getPath() == OBJECT_PATH.value) matchingMessageReceived.value = true
            }, return_slot)
        }.invoke()
        GC.collect()
        usleep(5_000u)

        fixture.m_adaptor?.emitSimpleSignal();

        assertFalse(waitUntil(matchingMessageReceived, 1.seconds));
    }

    @OptIn(NativeRuntimeApi::class)
    @Test
    fun canAddFloatingMatchRule(): Unit {
        val matchingMessageReceived = atomic(false)
        val internal = {
            val matchRule = "sender='${SERVICE_NAME.value}',path='${OBJECT_PATH.value}'";
            val con = createBusConnection()
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
        internal.invoke()
        GC.collect()
        usleep(5_000u)
        fixture.m_adaptor?.emitSimpleSignal();

        assertFalse(waitUntil(matchingMessageReceived, 1.seconds));
    }

    @Test
    fun willNotPassToMatchCallbackMessagesThatDoNotMatchTheRule(): Unit = memScoped {
        val matchRule = "type='signal',interface='${INTERFACE_NAME.value}',member='simpleSignal'";
        val numberOfMatchingMessages = atomic(0.convert<size_t>());
        val slot = s_proxyConnection.addMatch(matchRule, { msg: Message ->
            if (msg.getMemberName() == "simpleSignal") {
                numberOfMatchingMessages.value++;
            }
        }, return_slot)
        val adaptor2 = TestAdaptor(s_adaptorConnection, OBJECT_PATH_2);

        fixture.m_adaptor?.emitSignalWithMap(emptyMap());
        adaptor2.emitSimpleSignal();
        fixture.m_adaptor?.emitSimpleSignal();

        assertTrue(waitUntil({ numberOfMatchingMessages.value == 2u.convert<size_t>() }))
        assertFalse(waitUntil({ numberOfMatchingMessages.value > 2u }, 1.seconds))
    }

    // A simple direct connection test similar in nature to https://github.com/systemd/systemd/blob/main/src/libsystemd/sd-bus/test-bus-server.c
    @Test
    fun canBeUsedBetweenClientAndServer(): Unit = memScoped {
        val v = fixture.m_proxy!!.sumArrayItems(
            listOf(1u.toUShort(), 7u.toUShort()),
            arrayOf(2u, 3u, 4u)
        );
        fixture.m_adaptor?.emitSimpleSignal();

        // Make sure method call passes and emitted signal is received
        assertEquals(1u + 7u + 2u + 3u + 4u, v);
        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal));
    }
}

class DirectConnectionTest : BaseTest() {
    private val fixture = TextFixtureWithDirectConnection(this)

    // A simple direct connection test similar in nature to https://github.com/systemd/systemd/blob/main/src/libsystemd/sd-bus/test-bus-server.c
    @Test
    fun canBeUsedBetweenClientAndServer(): Unit {
        val v = fixture.m_proxy!!.sumArrayItems(
            listOf(1u.toUShort(), 7u.toUShort()),
            arrayOf(2u, 3u, 4u)
        );
        fixture.m_adaptor?.emitSimpleSignal();

        // Make sure method call passes and emitted signal is received
        assertEquals(1u + 7u + 2u + 3u + 4u, v);
        assertTrue(waitUntil(fixture.m_proxy!!.m_gotSimpleSignal));
        println("Done with test")
    }
}
