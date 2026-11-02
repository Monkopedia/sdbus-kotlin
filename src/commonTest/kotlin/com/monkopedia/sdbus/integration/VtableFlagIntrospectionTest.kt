package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.CONST_PROPERTY_VALUE
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_INVALIDATION_SIGNAL
import com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags.EMITS_NO_SIGNAL
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.interfaceFlags
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.signal
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * Cross-backend introspection round-trip: register vtable items with known flags, serve them, ask
 * the running adaptor for its own `org.freedesktop.DBus.Introspectable.Introspect` XML, and pin
 * what came back.
 *
 * The codegen goldens verify what the *generator emits*; before this test nothing verified what a
 * *running adaptor advertises*. That gap is why #197 (`METHOD_NO_REPLY` OR-ed with a literal zero
 * on native) went unnoticed and why #193's divergence table was wrong about a third of its scope.
 *
 * The two backends genuinely differ, so this pins each one rather than asserting a shared
 * contract. The divergence lives in the `backendServes*` capability flags, whose per-target
 * `actual`s are the record -- a backend that gained or lost a flag flips its `actual` rather than
 * weakening an assertion here. Refs #193.
 */
class VtableFlagIntrospectionTest {

    @Test
    fun deprecatedOnMembersIsServedByBothBackends() = runBlocking {
        val iface = InterfaceName("com.monkopedia.sdbus.flags.Members")
        val xml = introspectServedObject(
            iface to {
                method(MethodName("DeprecatedMethod")) {
                    isDeprecated = true
                    call<Unit> { }
                }
                signal(SignalName("DeprecatedSignal")) {
                    isDeprecated = true
                    with<Int>("value")
                }
                prop(PropertyName("DeprecatedProperty")) {
                    isDeprecated = true
                    withGetter { 42 }
                }
            }
        )

        for ((tag, name) in listOf(
            "method" to "DeprecatedMethod",
            "signal" to "DeprecatedSignal",
            "property" to "DeprecatedProperty"
        )) {
            xml.assertAnnotation(tag, name, DEPRECATED_ANNOTATION, "true", served = true)
        }
    }

    @Test
    fun deprecatedOnTheInterfaceIsServedOnlyWhereTheBackendCarriesIt() = runBlocking {
        val iface = InterfaceName("com.monkopedia.sdbus.flags.DeprecatedInterface")
        val xml = introspectServedObject(
            iface to {
                interfaceFlags { isDeprecated = true }
                // Deliberately unannotated, so any Deprecated annotation inside the interface
                // element must be the interface-level one.
                method(MethodName("Plain")) { call<Unit> { } }
            }
        )

        xml.assertAnnotation(
            "interface",
            iface.value,
            DEPRECATED_ANNOTATION,
            "true",
            served = backendServesInterfaceLevelDeprecated
        )
        xml.assertAnnotation("method", "Plain", DEPRECATED_ANNOTATION, "true", served = false)
    }

    @Test
    fun emitsChangedSignalIsServedOnlyWhereTheBackendCarriesIt() = runBlocking {
        val iface = InterfaceName("com.monkopedia.sdbus.flags.Properties")
        val xml = introspectServedObject(
            iface to {
                prop(PropertyName("ConstProperty")) {
                    +CONST_PROPERTY_VALUE
                    withGetter { 1 }
                }
                prop(PropertyName("InvalidatingProperty")) {
                    +EMITS_INVALIDATION_SIGNAL
                    withGetter { 2 }
                }
                prop(PropertyName("SilentProperty")) {
                    +EMITS_NO_SIGNAL
                    withGetter { 3 }
                }
            }
        )

        // The D-Bus default ("true") is left implicit by sd-bus, so only these three values can be
        // observed at all.
        for ((name, value) in listOf(
            "ConstProperty" to "const",
            "InvalidatingProperty" to "invalidates",
            "SilentProperty" to "false"
        )) {
            xml.assertAnnotation(
                "property",
                name,
                EMITS_CHANGED_SIGNAL_ANNOTATION,
                value,
                served = backendServesEmitsChangedSignal
            )
        }
    }

    @Test
    fun methodNoReplyIsServedOnlyWhereTheBackendCarriesIt() = runBlocking {
        val iface = InterfaceName("com.monkopedia.sdbus.flags.NoReply")
        val xml = introspectServedObject(
            iface to {
                method(MethodName("FireAndForget")) {
                    hasNoReply = true
                    call<Unit> { }
                }
            }
        )

        xml.assertAnnotation(
            "method",
            "FireAndForget",
            METHOD_NO_REPLY_ANNOTATION,
            "true",
            served = backendServesMethodNoReply
        )
    }
}
