package com.monkopedia.sdbus.integration

/**
 * Per-backend capabilities for [EmptyCollectionWireTest].
 *
 * The native (sd-bus) backend keeps the real wire signature on a received message, so an empty
 * collection can be peeked back as e.g. `as` / `a{sv}`. The JVM (pure-Java dbus) backend
 * reconstructs the body as plain Kotlin values with no retained per-element type, so peeking an
 * empty collection can only ever report the bare container (`a` / `a{}`). The send-side
 * declared-signature fix (so the daemon does not drop the connection) and the round-trip back to
 * an empty collection are exercised on both backends regardless.
 */
internal expect object EmptyCollectionWireSupport {
    /** True when peeking a received empty collection reports its full element signature. */
    val preservesWireSignatureForEmptyCollections: Boolean
}
