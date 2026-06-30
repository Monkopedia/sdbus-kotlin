package com.monkopedia.sdbus.integration

// The JVM wire backend now threads Signal.setDestination onto the outgoing wire message, so the
// daemon unicast-routes the directed signal and the recipient sees the destination header (#137).
internal actual val backendDeliversDirectedSignalsUnicast: Boolean = true
