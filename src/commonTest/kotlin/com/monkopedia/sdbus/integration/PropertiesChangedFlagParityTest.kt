package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.CONST_PROPERTY_VALUE
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_INVALIDATION_SIGNAL
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertiesProxy
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.SdbusException
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.onSignal
import com.monkopedia.sdbus.prop
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Cross-backend parity for the `PropertiesChanged` PAYLOAD against each property's
 * [com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags].
 *
 * `EmitsChangedSignal` is not introspection decoration -- it is an instruction to the emitter, and
 * sd-bus acts on it in `emit_properties_changed_on_interface` (`bus-objects.c`). The assertions
 * here are UNCONDITIONAL on both backends on purpose: unlike the introspection rows in
 * [TestBackendCapabilities], this is a payload contract a client can observe, so a backend that
 * disagrees is wrong rather than merely different. Refs #193.
 */
class PropertiesChangedFlagParityTest {

    private class Fixture(
        val obj: Object,
        val changed: CompletableDeferred<Pair<Map<PropertyName, Variant>, List<PropertyName>>>
    )

    /**
     * Serves one interface carrying a default property, an invalidating one, a const one and a
     * silent one, with a proxy already subscribed to `PropertiesChanged` for it.
     */
    private suspend fun withFixture(body: suspend (Fixture, InterfaceName) -> Unit) {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.pcflags$id")
        val path = ObjectPath("/com/monkopedia/sdbus/pcflags$id")
        val iface = InterfaceName("com.monkopedia.sdbus.pcflags$id.Iface")

        val server = createBusConnection(service)
        val client = createBusConnection()
        val obj = createObject(server, path)
        val reg = obj.addVTable(iface) {
            prop(CHANGING) { withGetter { 1 } }
            prop(INVALIDATING) {
                +EMITS_INVALIDATION_SIGNAL
                withGetter { 2 }
            }
            prop(CONST) {
                +CONST_PROPERTY_VALUE
                withGetter { 3 }
            }
            prop(SILENT) {
                +EMITS_NO_SIGNAL
                withGetter { 4 }
            }
        }
        server.startEventLoop()
        client.startEventLoop()
        val proxy = createProxy(client, service, path)

        val seen =
            CompletableDeferred<Pair<Map<PropertyName, Variant>, List<PropertyName>>>()
        val sigReg = proxy.onSignal(
            PropertiesProxy.INTERFACE_NAME,
            SignalName("PropertiesChanged")
        ) {
            call {
                    changedInterface: InterfaceName,
                    changed: Map<PropertyName, Variant>,
                    invalidated: List<PropertyName>
                ->
                if (changedInterface == iface) seen.complete(changed to invalidated)
            }
        }

        try {
            body(Fixture(obj, seen), iface)
        } finally {
            sigReg.release()
            reg.release()
            proxy.release()
            obj.release()
            client.stopEventLoop()
            server.stopEventLoop()
            client.release()
            server.release()
        }
    }

    @Test
    fun noArgEmissionSkipsPropertiesThatDoNotEmitChange() = runBlocking {
        withFixture { fixture, iface ->
            fixture.obj.emitPropertiesChangedSignal(iface)

            val (changed, invalidated) = withTimeout(5_000) { fixture.changed.await() }
            // sd-bus's names==NULL path: EMITS_INVALIDATION goes to the name-only array, anything
            // without EMITS_CHANGE is skipped entirely, everything else carries its value.
            assertEquals(setOf(CHANGING), changed.keys, "changed_properties")
            assertEquals(1, changed.getValue(CHANGING).get<Int>())
            assertEquals(listOf(INVALIDATING), invalidated, "invalidated_properties")
        }
    }

    @Test
    fun namedEmissionPutsAnInvalidatingPropertyInTheNameOnlyArray() = runBlocking {
        withFixture { fixture, iface ->
            fixture.obj.emitPropertiesChangedSignal(iface, listOf(INVALIDATING))

            val (changed, invalidated) = withTimeout(5_000) { fixture.changed.await() }
            assertEquals(emptySet<PropertyName>(), changed.keys, "changed_properties")
            assertEquals(listOf(INVALIDATING), invalidated, "invalidated_properties")
        }
    }

    @Test
    fun namedEmissionOfAConstPropertyIsRejected() = runBlocking {
        withFixture { fixture, iface ->
            // sd-bus's named path does NOT silently skip: it assert_returns -EDOM for a property
            // carrying neither EMITS_CHANGE nor EMITS_INVALIDATION, aborting the whole signal.
            assertFailsWith<SdbusException> {
                fixture.obj.emitPropertiesChangedSignal(iface, listOf(CONST))
            }
        }
    }

    @Test
    fun namedEmissionOfASilentPropertyIsRejected() = runBlocking {
        withFixture { fixture, iface ->
            assertFailsWith<SdbusException> {
                fixture.obj.emitPropertiesChangedSignal(iface, listOf(SILENT))
            }
        }
    }

    private companion object {
        val CHANGING = PropertyName("Changing")
        val INVALIDATING = PropertyName("Invalidating")
        val CONST = PropertyName("Const")
        val SILENT = PropertyName("Silent")
    }
}
