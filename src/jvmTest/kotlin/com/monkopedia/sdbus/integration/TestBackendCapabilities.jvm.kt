package com.monkopedia.sdbus.integration

// The JVM wire backend now threads Signal.setDestination onto the outgoing wire message, so the
// daemon unicast-routes the directed signal and the recipient sees the destination header (#137).
internal actual val backendDeliversDirectedSignalsUnicast: Boolean = true

// WireServeRegistry captures the interface-level deprecated bit off InterfaceFlagsVTableItem and
// WireServe writes it as a child of <interface>, matching what sd-bus emits for
// SD_BUS_VTABLE_DEPRECATED on the vtable start item (#193 spike).
internal actual val backendServesInterfaceLevelDeprecated: Boolean = true

// WireServeRegistry captures each property's PropertyUpdateBehaviorFlags and WireServe writes
// const / invalidates / false; the D-Bus default ("true") is left implicit, as sd-bus does
// (#193 spike).
internal actual val backendServesEmitsChangedSignal: Boolean = true

// Still false: hasNoReply drives the reply-suppression path only, never the introspection XML.
// Out of scope for #193 -- the native half of this row was fixed separately in #216/#197.
internal actual val backendServesMethodNoReply: Boolean = false
