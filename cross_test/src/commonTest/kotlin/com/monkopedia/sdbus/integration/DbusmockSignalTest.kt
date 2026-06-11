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

import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.signalFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Signal coverage against the python-dbusmock independent peer (issue #36): subscription via
 * [onSignal], reactive collection via [signalFlow], payload shapes encoded by the foreign
 * (Python/GDBus) stack, and the subscribe/unsubscribe lifecycle.
 *
 * Lives in commonTest so the exact same assertions run against BOTH the native sd-bus backend
 * (`linuxX64Test`) and the JVM dbus-java backend (`jvmTest`). Skips cleanly when
 * python3-dbusmock is not installed (see [DbusmockHarness]).
 */
class DbusmockSignalTest {

    @Test
    fun onSignal_deliversForeignEmittedPayloadShapes() = withDbusmockPeer("SigShapes") {
        val noArgs = CompletableDeferred<Unit>()
        val intArg = CompletableDeferred<Int>()
        val multiArg = CompletableDeferred<Pair<Long, String>>()
        val stringList = CompletableDeferred<List<String>>()
        val dict = CompletableDeferred<Map<String, Variant>>()
        val big = CompletableDeferred<ULong>()

        val registrations = listOf(
            proxy.onSignal(iface, SignalName("NoArgs")) {
                // Explicit type argument selects the zero-parameter handler overload.
                call<Boolean> { noArgs.complete(Unit) }
            },
            proxy.onSignal(iface, SignalName("IntArg")) {
                call { value: Int -> intArg.complete(value) }
            },
            proxy.onSignal(iface, SignalName("MultiArg")) {
                call { x: Long, s: String -> multiArg.complete(x to s) }
            },
            proxy.onSignal(iface, SignalName("StringList")) {
                call { values: List<String> -> stringList.complete(values) }
            },
            proxy.onSignal(iface, SignalName("Dict")) {
                call { values: Map<String, Variant> -> dict.complete(values) }
            },
            proxy.onSignal(iface, SignalName("Big")) {
                call { value: ULong -> big.complete(value) }
            }
        )

        try {
            // The registrations above sent their AddMatch before these EmitSignal control
            // calls, so the bus routes every emission below back to us.
            emitSignal("NoArgs", "")
            emitSignal("IntArg", "i", listOf(Variant(-42)))
            emitSignal(
                "MultiArg",
                "xs",
                listOf(Variant(-5_000_000_000L), Variant("payload from foreign peer"))
            )
            emitSignal("StringList", "as", listOf(Variant(listOf("a", "", "déjà-vu"))))
            emitSignal(
                "Dict",
                "a{sv}",
                listOf(Variant(mapOf("count" to Variant(7), "name" to Variant("widget"))))
            )
            emitSignal("Big", "t", listOf(Variant(ULong.MAX_VALUE)))

            withTimeout(10_000) { noArgs.await() }
            assertEquals(-42, withTimeout(10_000) { intArg.await() })
            assertEquals(
                -5_000_000_000L to "payload from foreign peer",
                withTimeout(10_000) { multiArg.await() }
            )
            assertEquals(listOf("a", "", "déjà-vu"), withTimeout(10_000) { stringList.await() })
            val dictValue = withTimeout(10_000) { dict.await() }
            assertEquals(setOf("count", "name"), dictValue.keys)
            assertEquals(7, dictValue.getValue("count").get<Int>())
            assertEquals("widget", dictValue.getValue("name").get<String>())
            assertEquals(ULong.MAX_VALUE, withTimeout(10_000) { big.await() })
        } finally {
            registrations.forEach { it.release() }
        }
    }

    @Test
    fun signalFlow_emitsForeignSignalsInOrder() = withDbusmockPeer("SigFlow") {
        coroutineScope {
            val received = Channel<Int>(Channel.UNLIMITED)
            val collector = launch {
                proxy.signalFlow<Int>(iface, SignalName("Counted")) {
                    call { value: Int -> value }
                }.collect { received.trySend(it) }
            }

            // signalFlow registers its handler only once collection starts; poke the peer
            // with marker payloads until the first delivery proves the subscription is live.
            pumpUntilSubscribed(received) {
                emitSignal("Counted", "i", listOf(Variant(MARKER)))
            }

            emitSignal("Counted", "i", listOf(Variant(1)))
            emitSignal("Counted", "i", listOf(Variant(2)))
            emitSignal("Counted", "i", listOf(Variant(3)))

            // All markers were emitted before the real payloads, and D-Bus preserves the
            // ordering of messages from one sender to one receiver.
            val seen = mutableListOf<Int>()
            withTimeout(10_000) {
                while (seen.size < 3) {
                    val value = received.receive()
                    if (value != MARKER) seen += value
                }
            }
            assertEquals(listOf(1, 2, 3), seen)

            // Cancelling collection releases the registration through awaitClose.
            collector.cancelAndJoin()
        }
    }

    @Test
    fun onSignal_unsubscribeStopsDelivery_andResubscribeWorks() = withDbusmockPeer("SigLifecycle") {
        val events = Channel<Int>(Channel.UNLIMITED)
        val flushes = Channel<Int>(Channel.UNLIMITED)
        val flushRegistration = proxy.onSignal(iface, SignalName("Flush")) {
            call { value: Int ->
                flushes.trySend(value)
                Unit
            }
        }

        try {
            var registration = proxy.onSignal(iface, SignalName("Evt")) {
                call { value: Int ->
                    events.trySend(value)
                    Unit
                }
            }
            emitSignal("Evt", "i", listOf(Variant(1)))
            assertEquals(1, withTimeout(10_000) { events.receive() })

            // Unsubscribe, then emit again. The Flush signal (whose handler is still live)
            // is emitted after Evt#2, so once it arrives, Evt#2 would have been delivered
            // too if the released subscription were still active.
            registration.release()
            emitSignal("Evt", "i", listOf(Variant(2)))
            emitSignal("Flush", "i", listOf(Variant(99)))
            assertEquals(99, withTimeout(10_000) { flushes.receive() })
            assertTrue(
                events.tryReceive().isFailure,
                "signal was delivered to a released subscription"
            )

            // Re-subscribing during the proxy lifetime resumes delivery.
            registration = proxy.onSignal(iface, SignalName("Evt")) {
                call { value: Int ->
                    events.trySend(value)
                    Unit
                }
            }
            emitSignal("Evt", "i", listOf(Variant(3)))
            assertEquals(3, withTimeout(10_000) { events.receive() })
            registration.release()
        } finally {
            flushRegistration.release()
        }
    }

    private companion object {
        /** Payload used to detect when a flow subscription has gone live; see callers. */
        private const val MARKER = -1
    }
}
