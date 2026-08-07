package com.monkopedia.sdbus.integration

// sd-bus routes directed signals to the destination alone and exposes the destination header.
internal actual val backendDeliversDirectedSignalsUnicast: Boolean = true

// sd-bus writes the interface's SD_BUS_VTABLE_DEPRECATED bit into the introspection it generates.
internal actual val backendServesInterfaceLevelDeprecated: Boolean = true

// sd-bus writes const / invalidates / false for the property emit bits (the D-Bus default, "true",
// is deliberately left implicit).
internal actual val backendServesEmitsChangedSignal: Boolean = true

// sd-bus writes org.freedesktop.DBus.Method.NoReply for SD_BUS_VTABLE_METHOD_NO_REPLY. Until #197
// that bit never reached it: Flags.toSdBusMethodFlags OR-ed the branch with a literal 0u.
internal actual val backendServesMethodNoReply: Boolean = true
