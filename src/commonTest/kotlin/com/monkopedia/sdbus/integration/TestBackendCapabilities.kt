package com.monkopedia.sdbus.integration

/**
 * Whether the active backend delivers a directed (unicast) signal — one with a `DESTINATION` set
 * via [com.monkopedia.sdbus.Signal.setDestination] — only to the targeted peer, and re-exposes the
 * destination header to the receiving handler.
 *
 * - **native (sd-bus): `true`** — the bus routes the signal to the destination alone and the
 *   recipient sees the `DESTINATION` header.
 * - **JVM wire backend: `false`** — KNOWN LIMITATION: `setDestination` is accepted but the signal
 *   is still delivered to every matching subscriber (broadcast), and the inbound handler does not
 *   see the destination header. Tracked as a backend follow-up; flip this to `true` when the JVM
 *   backend honors unicast signal routing, at which point
 *   [ExternalApiCoverageTest.directedSignal_isDeliveredOnlyToTargetedProxy] will enforce it.
 */
internal expect val backendDeliversDirectedSignalsUnicast: Boolean
