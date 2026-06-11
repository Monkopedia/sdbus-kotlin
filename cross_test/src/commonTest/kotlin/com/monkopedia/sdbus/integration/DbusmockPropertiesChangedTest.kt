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
import com.monkopedia.sdbus.PropertiesProxy
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.propDelegate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Foreign-emitted `PropertiesChanged` coverage against the python-dbusmock independent peer
 * (issue #36): real **changed values** (dbusmock's `UpdateProperties` emits the new values, not
 * just invalidations) plus scripted invalidation-only signals, observed through both the raw
 * [PropertiesProxy.registerPropertiesProxy] callback and the reactive
 * `PropertyDelegate.changes()`/`changesOrNull()`/`values()`/`valuesOrNull()` flows.
 *
 * Lives in commonTest so the exact same assertions run against BOTH the native sd-bus backend
 * (`linuxX64Test`) and the JVM dbus-java backend (`jvmTest`) — settling the JVM-vs-native
 * parity question for changed-value payloads from an independent emitter. Skips cleanly when
 * python3-dbusmock is not installed (see [DbusmockHarness]).
 */
class DbusmockPropertiesChangedTest {

    @Test
    fun propertiesChanged_carriesChangedValuesAndInvalidatedNames() = withDbusmockPeer("PropSig") {
        addProperty("Level", Variant(1))
        addProperty("Mode", Variant("idle"))

        val events = Channel<Triple<InterfaceName, Map<PropertyName, Variant>, List<PropertyName>>>(
            Channel.UNLIMITED
        )
        // onSignal-based registration is active as soon as this returns; no pumping needed.
        PropertiesProxy(proxy).registerPropertiesProxy { interfaceName, changed, invalidated ->
            events.trySend(Triple(interfaceName, changed, invalidated))
        }

        // Changed values: dbusmock emits the real new values in the changed map.
        updateProperties(mapOf("Level" to Variant(5), "Mode" to Variant("busy")))
        val changedEvent = withTimeout(10_000) { events.receive() }
        assertEquals(iface, changedEvent.first)
        assertEquals(5, changedEvent.second[PropertyName("Level")]?.get<Int>())
        assertEquals("busy", changedEvent.second[PropertyName("Mode")]?.get<String>())
        assertTrue(changedEvent.third.isEmpty(), "no properties should be invalidated")

        // Invalidation-only: empty changed map, names listed as invalidated.
        emitPropertiesChanged(invalidated = listOf("Mode"))
        val invalidatedEvent = withTimeout(10_000) { events.receive() }
        assertEquals(iface, invalidatedEvent.first)
        assertTrue(invalidatedEvent.second.isEmpty(), "no changed values expected")
        assertEquals(listOf(PropertyName("Mode")), invalidatedEvent.third)

        // Mixed: one property changes value while another is invalidated, in a single signal.
        emitPropertiesChanged(invalidated = listOf("Mode"), changed = mapOf("Level" to Variant(9)))
        val mixedEvent = withTimeout(10_000) { events.receive() }
        assertEquals(9, mixedEvent.second[PropertyName("Level")]?.get<Int>())
        assertEquals(listOf(PropertyName("Mode")), mixedEvent.third)
    }

    @Test
    fun propertyDelegate_changes_emitsForeignChangedValues_andDropsInvalidations() =
        withDbusmockPeer("PropChanges") {
            addProperty("Level", Variant(1))
            val delegate = proxy.propDelegate<Any?, Int>(iface, PropertyName("Level"))
            assertEquals(1, delegate.get())

            coroutineScope {
                val received = Channel<Int>(Channel.UNLIMITED)
                val collector = launch {
                    delegate.changes().collect { received.trySend(it) }
                }
                pumpUntilSubscribed(received) { updateProperty(nextMarker()) }

                updateProperty(7)
                assertEquals(7, received.nextNonMarker())

                // Invalidation events are dropped by changes(): after invalidating, the very
                // next emission must be the subsequent real value.
                emitPropertiesChanged(invalidated = listOf("Level"))
                updateProperty(8)
                assertEquals(8, received.nextNonMarker())

                collector.cancelAndJoin()
            }
        }

    @Test
    fun propertyDelegate_changesOrNull_emitsNullOnForeignInvalidation() =
        withDbusmockPeer("PropChangesNull") {
            addProperty("Level", Variant(1))
            val delegate = proxy.propDelegate<Any?, Int>(iface, PropertyName("Level"))

            coroutineScope {
                val received = Channel<Int?>(Channel.UNLIMITED)
                val collector = launch {
                    delegate.changesOrNull().collect { received.trySend(it) }
                }
                pumpUntilSubscribed(received) { updateProperty(nextMarker()) }

                updateProperty(5)
                assertEquals(5, received.nextNonMarker())

                emitPropertiesChanged(invalidated = listOf("Level"))
                assertNull(received.nextNonMarker(), "invalidation should surface as null")

                updateProperty(6)
                assertEquals(6, received.nextNonMarker())

                collector.cancelAndJoin()
            }
        }

    @Test
    fun propertyDelegate_values_emitsCurrentValueThenForeignChanges() =
        withDbusmockPeer("PropValues") {
            addProperty("Level", Variant(21))
            val delegate = proxy.propDelegate<Any?, Int>(iface, PropertyName("Level"))

            coroutineScope {
                val received = Channel<Int>(Channel.UNLIMITED)
                val collector = launch {
                    delegate.values().collect { received.trySend(it) }
                }
                // The current value is read (a real Get round-trip to the peer) and emitted
                // before the change subscription goes live.
                assertEquals(21, withTimeout(10_000) { received.receive() })

                pumpUntilSubscribed(received) { updateProperty(nextMarker()) }
                updateProperty(22)
                assertEquals(22, received.nextNonMarker())

                collector.cancelAndJoin()
            }
        }

    @Test
    fun propertyDelegate_valuesOrNull_emitsInitialThenNullOnInvalidation() =
        withDbusmockPeer("PropValuesNull") {
            addProperty("Level", Variant(31))
            val delegate = proxy.propDelegate<Any?, Int>(iface, PropertyName("Level"))

            coroutineScope {
                val received = Channel<Int?>(Channel.UNLIMITED)
                val collector = launch {
                    delegate.valuesOrNull().collect { received.trySend(it) }
                }
                assertEquals(31, withTimeout(10_000) { received.receive() })

                pumpUntilSubscribed(received) { updateProperty(nextMarker()) }
                emitPropertiesChanged(invalidated = listOf("Level"))
                assertNull(received.nextNonMarker(), "invalidation should surface as null")

                updateProperty(32)
                assertEquals(32, received.nextNonMarker())

                collector.cancelAndJoin()
            }
        }

    /** Updates the `Level` property on the peer, triggering a foreign PropertiesChanged. */
    private fun DbusmockPeer.updateProperty(value: Int) =
        updateProperties(mapOf("Level" to Variant(value)))

    private var marker = MARKER_BASE

    private fun nextMarker(): Int = marker++

    /**
     * Receives the next element that is not a subscription-probe marker (see
     * [pumpUntilSubscribed]); markers are always emitted before the real test values, so
     * skipping them preserves the assertion ordering.
     */
    private suspend fun ReceiveChannel<Int?>.nextNonMarker(): Int? = withTimeout(10_000) {
        var value: Int? = MARKER_BASE
        while (value != null && value >= MARKER_BASE) {
            value = receive()
        }
        value
    }

    private companion object {
        /** Probe values are >= this; real test values must stay below it. */
        private const val MARKER_BASE = 1_000
    }
}
