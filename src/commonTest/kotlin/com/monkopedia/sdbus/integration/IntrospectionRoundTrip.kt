package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.VTableBuilder
import com.monkopedia.sdbus.VTableItem
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.fail

/** The standard D-Bus annotation marking an element deprecated. */
internal const val DEPRECATED_ANNOTATION = "org.freedesktop.DBus.Deprecated"

/** The standard D-Bus annotation marking a method as expecting no reply. */
internal const val METHOD_NO_REPLY_ANNOTATION = "org.freedesktop.DBus.Method.NoReply"

/** The standard D-Bus annotation declaring how a property signals changes. */
internal const val EMITS_CHANGED_SIGNAL_ANNOTATION =
    "org.freedesktop.DBus.Property.EmitsChangedSignal"

/**
 * Serves an object carrying [vtables] on a real bus, calls
 * `org.freedesktop.DBus.Introspectable.Introspect` against it, and returns the XML it advertised.
 *
 * This is the instrument for asserting what a *running adaptor serves*, as opposed to what the
 * codegen goldens already cover — what the *generator emits*. Nothing tested the former until
 * [VtableFlagIntrospectionTest], which is how #197 survived. It lives in `commonTest` so every
 * assertion built on it runs against both backends.
 *
 * The connections, object and registrations are all torn down before returning; only the XML
 * escapes.
 */
internal suspend fun introspectServedObject(
    vararg vtables: Pair<InterfaceName, VTableBuilder.() -> Unit>
): IntrospectionXml {
    val id = Random.nextInt(100_000, 999_999)
    val service = ServiceName("com.monkopedia.sdbus.introspect$id")
    val path = ObjectPath("/com/monkopedia/sdbus/introspect$id")

    val server = createBusConnection(service)
    val client = createBusConnection()
    val obj = createObject(server, path)
    val registrations = vtables.map { (name, vtable) ->
        obj.addVTable(name, buildList<VTableItem> { VTableBuilder(this).vtable() })
    }
    server.startEventLoop()
    val proxy = createProxy(client, service, path, runEventLoopThread = false)

    try {
        return IntrospectionXml(
            proxy.callMethod<String>(
                InterfaceName("org.freedesktop.DBus.Introspectable"),
                MethodName("Introspect")
            ) { }
        )
    } finally {
        registrations.forEach(Resource::release)
        proxy.release()
        obj.release()
        server.stopEventLoop()
        client.release()
        server.release()
    }
}

/**
 * The introspection XML a live adaptor advertised, with just enough structure to assert on a single
 * element's annotations.
 *
 * D-Bus introspection never nests an element inside another of the same tag, so locating
 * `<tag name="...">` and taking everything up to the following `</tag>` isolates exactly that
 * element -- which is why this needs no XML parser on the test classpath.
 */
internal class IntrospectionXml(val raw: String) {

    /**
     * The children of `<[tag] name="[name]">`, or the empty string when the element is
     * self-closing and so carries no annotations at all.
     */
    fun childrenOf(tag: String, name: String): String {
        val open = raw.indexOf("<$tag name=\"$name\"")
        if (open < 0) fail("no <$tag name=\"$name\"> in the served introspection:\n$raw")
        val openEnd = raw.indexOf('>', open)
        if (raw[openEnd - 1] == '/') return ""
        val close = raw.indexOf("</$tag>", openEnd)
        if (close < 0) fail("unterminated <$tag name=\"$name\"> in introspection:\n$raw")
        return raw.substring(openEnd + 1, close)
    }

    /**
     * Asserts whether `<[tag] name="[name]">` carries [annotation] with [value], pinning both
     * polarities: pass `served = false` to assert the backend does *not* advertise it.
     */
    fun assertAnnotation(
        tag: String,
        name: String,
        annotation: String,
        value: String,
        served: Boolean
    ) {
        val expected = "<annotation name=\"$annotation\" value=\"$value\"/>"
        assertEquals(
            served,
            expected in childrenOf(tag, name),
            "$annotation=\"$value\" on <$tag name=\"$name\"> should be " +
                "${if (served) "served" else "absent"} by this backend; full XML:\n$raw"
        )
    }
}
