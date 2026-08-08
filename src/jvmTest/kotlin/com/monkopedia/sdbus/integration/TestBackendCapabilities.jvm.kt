package com.monkopedia.sdbus.integration

// The JVM wire backend now threads Signal.setDestination onto the outgoing wire message, so the
// daemon unicast-routes the directed signal and the recipient sees the destination header (#137).
internal actual val backendDeliversDirectedSignalsUnicast: Boolean = true

// WireServe builds the introspection XML from WireServeRegistry, which captures the per-member
// deprecated bit and no other vtable flag -- so nothing interface-level reaches the XML.
internal actual val backendServesInterfaceLevelDeprecated: Boolean = false

// Same reason: the wire backend records no property update behavior to advertise.
internal actual val backendServesEmitsChangedSignal: Boolean = false

// Same reason: hasNoReply drives the reply-suppression path only, never the introspection XML.
internal actual val backendServesMethodNoReply: Boolean = false

// KNOWN JVM/NATIVE DIVERGENCE, #193 -- OPEN, not an accepted limitation (see the expect
// declaration). WireDbusObject reads no PropertyUpdateBehaviorFlags, so every property is emitted
// with its current value whichever flags it carries, and nothing is ever rejected.
internal actual val backendHonoursEmitsChangedSignalOnEmit: Boolean = false
