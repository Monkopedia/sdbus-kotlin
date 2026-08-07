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

/**
 * Whether a served object advertises `org.freedesktop.DBus.Deprecated` for an interface marked
 * deprecated via [com.monkopedia.sdbus.interfaceFlags].
 *
 * The same annotation on a *member* is served by both backends, so only the interface-level case
 * needs a lever. Measured by [VtableFlagIntrospectionTest]; tracked in #193.
 */
internal expect val backendServesInterfaceLevelDeprecated: Boolean

/**
 * Whether a served object advertises `org.freedesktop.DBus.Property.EmitsChangedSignal` for a
 * property registered with a non-default
 * [com.monkopedia.sdbus.Flags.PropertyUpdateBehaviorFlags].
 *
 * Measured by [VtableFlagIntrospectionTest]; tracked in #193.
 */
internal expect val backendServesEmitsChangedSignal: Boolean

/**
 * Whether a served object advertises `org.freedesktop.DBus.Method.NoReply` for a method registered
 * with `hasNoReply = true`.
 *
 * Unlike the two above this is not a backend capability but a defect on whichever backend reports
 * `false` -- both did until #197. Measured by [VtableFlagIntrospectionTest].
 */
internal expect val backendServesMethodNoReply: Boolean
