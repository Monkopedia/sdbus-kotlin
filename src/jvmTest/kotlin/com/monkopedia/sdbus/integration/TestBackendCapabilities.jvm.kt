package com.monkopedia.sdbus.integration

// KNOWN LIMITATION: the JVM wire backend accepts setDestination but still broadcasts the signal to
// all matching subscribers and does not re-expose the destination header. See the expect docs.
internal actual val backendDeliversDirectedSignalsUnicast: Boolean = false
