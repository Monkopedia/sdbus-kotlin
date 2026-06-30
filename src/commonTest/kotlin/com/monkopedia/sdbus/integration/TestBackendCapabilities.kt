package com.monkopedia.sdbus.integration

/**
 * Whether the active backend delivers a directed (unicast) signal — one with a `DESTINATION` set
 * via [com.monkopedia.sdbus.Signal.setDestination] — only to the targeted peer, and re-exposes the
 * destination header to the receiving handler.
 *
 * Both backends are `true` as of #137: native (sd-bus) always did, and the JVM wire backend now
 * threads the destination onto the outgoing wire message so the daemon unicast-routes it and the
 * recipient sees the `DESTINATION` header. The flag is retained so
 * [ExternalApiCoverageTest.directedSignal_isDeliveredOnlyToTargetedProxy] keeps a per-backend lever
 * (any backend that regressed to broadcast would flip its actual to `false` rather than silently
 * weaken the assertion).
 */
internal expect val backendDeliversDirectedSignalsUnicast: Boolean
