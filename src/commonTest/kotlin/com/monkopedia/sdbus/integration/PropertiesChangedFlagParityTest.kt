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
import kotlin.test.assertFalse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Pins what each backend puts in a `PropertiesChanged` signal for a property carrying a non-default
 * [com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags].
 *
 * `EmitsChangedSignal` is not introspection decoration -- it is an instruction to the emitter, and
 * sd-bus acts on it in `emit_properties_changed_on_interface` (`bus-objects.c`) with two distinct
 * rules, one per overload of [Object.emitPropertiesChangedSignal]:
 *
 * - **No explicit names** (`names == NULL`): a property flagged `EMITS_INVALIDATION` goes to the
 *   name-only array, a property with neither `EMITS_CHANGE` nor `EMITS_INVALIDATION` is skipped,
 *   and if that leaves nothing to say **no signal is sent at all**.
 * - **An explicit name list**: a named property carrying neither flag is not skipped, it is
 *   *rejected* -- `assert_return(..., -EDOM)` aborts the whole emission, so nothing is sent and
 *   `ConnectionImpl` turns the errno into a thrown [SdbusException].
 *
 * Nothing in this repo verified either rule before, which is a gap #193 itself raises: its
 * divergence table was derived from reading C rather than from a run. The JVM wire backend reads no
 * flags at all, so it disagrees on every case here -- that divergence is recorded in
 * [backendHonoursEmitsChangedSignalOnEmit] and pinned in both directions rather than asserted away.
 *
 * This test records reality and commits to nothing: closing the divergence is a breaking change
 * that PR #231 priced and deliberately did not merge, and #193 remains open. Refs #193.
 */
class PropertiesChangedFlagParityTest {

    private class Emission(
        val changed: Map<PropertyName, Variant>,
        val invalidated: List<PropertyName>
    )

    private class Fixture(
        val obj: Object,
        /** Carries [CHANGING], [INVALIDATING], [CONST] and [SILENT]. */
        val mixedIface: InterfaceName,
        /** Carries [CONST] and [SILENT] only, so nothing on it announces a change. */
        val quietIface: InterfaceName,
        val mixedEmission: CompletableDeferred<Emission>,
        val quietEmission: CompletableDeferred<Emission>
    )

    /**
     * Serves both interfaces on one object, with a proxy already subscribed to `PropertiesChanged`.
     */
    private suspend fun withFixture(body: suspend (Fixture) -> Unit) {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.pcflags$id")
        val path = ObjectPath("/com/monkopedia/sdbus/pcflags$id")
        val mixedIface = InterfaceName("com.monkopedia.sdbus.pcflags$id.Mixed")
        val quietIface = InterfaceName("com.monkopedia.sdbus.pcflags$id.Quiet")

        val server = createBusConnection(service)
        val client = createBusConnection()
        val obj = createObject(server, path)
        val mixedReg = obj.addVTable(mixedIface) {
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
        val quietReg = obj.addVTable(quietIface) {
            prop(CONST) {
                +CONST_PROPERTY_VALUE
                withGetter { 5 }
            }
            prop(SILENT) {
                +EMITS_NO_SIGNAL
                withGetter { 6 }
            }
        }
        server.startEventLoop()
        client.startEventLoop()
        val proxy = createProxy(client, service, path)

        val mixed = CompletableDeferred<Emission>()
        val quiet = CompletableDeferred<Emission>()
        val sigReg = proxy.onSignal(
            PropertiesProxy.INTERFACE_NAME,
            SignalName("PropertiesChanged")
        ) {
            call {
                    changedInterface: InterfaceName,
                    changed: Map<PropertyName, Variant>,
                    invalidated: List<PropertyName>
                ->
                val emission = Emission(changed, invalidated)
                if (changedInterface == mixedIface) mixed.complete(emission)
                if (changedInterface == quietIface) quiet.complete(emission)
            }
        }

        try {
            body(Fixture(obj, mixedIface, quietIface, mixed, quiet))
        } finally {
            sigReg.release()
            quietReg.release()
            mixedReg.release()
            proxy.release()
            obj.release()
            client.stopEventLoop()
            server.stopEventLoop()
            client.release()
            server.release()
        }
    }

    @Test
    fun noArgEmissionSkipsSilentPropertiesOnlyWhereTheBackendHonoursTheFlags() = runBlocking {
        withFixture { fixture ->
            fixture.obj.emitPropertiesChangedSignal(fixture.mixedIface)

            val emission = withTimeout(5_000) { fixture.mixedEmission.await() }
            if (backendHonoursEmitsChangedSignalOnEmit) {
                assertEquals(setOf(CHANGING), emission.changed.keys, "changed_properties")
                assertEquals(1, emission.changed.getValue(CHANGING).get<Int>())
                assertEquals(listOf(INVALIDATING), emission.invalidated, "invalidated_properties")
            } else {
                assertEquals(
                    setOf(CHANGING, INVALIDATING, CONST, SILENT),
                    emission.changed.keys,
                    "changed_properties"
                )
                assertEquals(2, emission.changed.getValue(INVALIDATING).get<Int>())
                assertEquals(emptyList(), emission.invalidated, "invalidated_properties")
            }
        }
    }

    @Test
    fun noArgEmissionSendsNothingAtAllWhereTheBackendHonoursTheFlags() = runBlocking {
        withFixture { fixture ->
            // Nothing on quietIface announces a change, so sd-bus's names==NULL path leaves the
            // message empty and returns without sending it. Emitting on mixedIface afterwards is
            // the sentinel: both backends send that one, and the daemon preserves per-sender order,
            // so its arrival proves the quiet emission is never coming rather than merely late.
            fixture.obj.emitPropertiesChangedSignal(fixture.quietIface)
            fixture.obj.emitPropertiesChangedSignal(fixture.mixedIface)

            withTimeout(5_000) { fixture.mixedEmission.await() }
            if (backendHonoursEmitsChangedSignalOnEmit) {
                assertFalse(
                    fixture.quietEmission.isCompleted,
                    "PropertiesChanged for an interface with nothing to announce"
                )
            } else {
                val emission = withTimeout(5_000) { fixture.quietEmission.await() }
                assertEquals(setOf(CONST, SILENT), emission.changed.keys, "changed_properties")
                assertEquals(emptyList(), emission.invalidated, "invalidated_properties")
            }
        }
    }

    @Test
    fun namedEmissionOfAnInvalidatingPropertyIsNameOnlyWhereTheBackendHonoursTheFlags() =
        runBlocking {
            withFixture { fixture ->
                fixture.obj.emitPropertiesChangedSignal(fixture.mixedIface, listOf(INVALIDATING))

                val emission = withTimeout(5_000) { fixture.mixedEmission.await() }
                if (backendHonoursEmitsChangedSignalOnEmit) {
                    assertEquals(emptySet(), emission.changed.keys, "changed_properties")
                    assertEquals(
                        listOf(INVALIDATING),
                        emission.invalidated,
                        "invalidated_properties"
                    )
                } else {
                    assertEquals(setOf(INVALIDATING), emission.changed.keys, "changed_properties")
                    assertEquals(2, emission.changed.getValue(INVALIDATING).get<Int>())
                    assertEquals(emptyList(), emission.invalidated, "invalidated_properties")
                }
            }
        }

    @Test
    fun namedEmissionOfAConstPropertyIsRejectedOnlyWhereTheBackendHonoursTheFlags() = runBlocking {
        withFixture { fixture ->
            assertNamedEmissionIsRejected(fixture, CONST, valueIfEmitted = 3)
        }
    }

    @Test
    fun namedEmissionOfASilentPropertyIsRejectedOnlyWhereTheBackendHonoursTheFlags() = runBlocking {
        withFixture { fixture ->
            assertNamedEmissionIsRejected(fixture, SILENT, valueIfEmitted = 4)
        }
    }

    /**
     * The named path rejects rather than skips, so on a backend honouring the flags the call throws
     * and nothing reaches the bus; elsewhere the property is emitted with its current value.
     */
    private suspend fun assertNamedEmissionIsRejected(
        fixture: Fixture,
        property: PropertyName,
        valueIfEmitted: Int
    ) {
        if (backendHonoursEmitsChangedSignalOnEmit) {
            assertFailsWith<SdbusException> {
                fixture.obj.emitPropertiesChangedSignal(fixture.mixedIface, listOf(property))
            }
        } else {
            fixture.obj.emitPropertiesChangedSignal(fixture.mixedIface, listOf(property))

            val emission = withTimeout(5_000) { fixture.mixedEmission.await() }
            assertEquals(setOf(property), emission.changed.keys, "changed_properties")
            assertEquals(valueIfEmitted, emission.changed.getValue(property).get<Int>())
            assertEquals(emptyList(), emission.invalidated, "invalidated_properties")
        }
    }

    private companion object {
        val CHANGING = PropertyName("Changing")
        val INVALIDATING = PropertyName("Invalidating")
        val CONST = PropertyName("Const")
        val SILENT = PropertyName("Silent")
    }
}
