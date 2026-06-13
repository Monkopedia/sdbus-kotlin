/**
 *
 * (C) 2024 - 2026 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * sdbus-kotlin. If not, see <https://www.gnu.org/licenses/>.
 */
package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.ActionResource
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.MethodVTableItem
import com.monkopedia.sdbus.PropertyVTableItem
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.SignalVTableItem
import com.monkopedia.sdbus.VTableItem
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Serves incoming D-Bus METHOD_CALL messages for objects exported on the self-owned wire
 * connection (epic #93 phase 4 — the core of #90). This is the piece that makes a JVM-exported
 * object reachable by EXTERNAL processes (busctl, dbus-send, another connection) over the bus, not
 * just in-process.
 *
 * Routing reuses the SAME registries the in-process client path uses:
 *  - regular methods, `org.freedesktop.DBus.Properties` Get/Set/GetAll and
 *    `org.freedesktop.DBus.ObjectManager` GetManagedObjects all resolve through [JvmStaticDispatch]
 *    (the handlers [WireDbusObject] registers in addVTable), so an object exported via addVTable
 *    is reachable both in-process AND over the wire from the one registration;
 *  - `org.freedesktop.DBus.Introspectable` Introspect generates interface XML from the vtable
 *    metadata captured in [WireServeRegistry];
 *  - `org.freedesktop.DBus.Peer` Ping / GetMachineId are answered directly.
 *
 * Threading: [handleIncomingCall] runs on [DBusWireConnection]'s serve worker (never the reader),
 * so a slow/async handler — or one that calls back on this same connection — cannot stall I/O.
 */
internal object WireServe {
    const val PROPERTIES_INTERFACE = "org.freedesktop.DBus.Properties"
    const val OBJECT_MANAGER_INTERFACE = "org.freedesktop.DBus.ObjectManager"
    const val INTROSPECTABLE_INTERFACE = "org.freedesktop.DBus.Introspectable"
    const val PEER_INTERFACE = "org.freedesktop.DBus.Peer"

    private const val ERROR_UNKNOWN_OBJECT = "org.freedesktop.DBus.Error.UnknownObject"
    private const val ERROR_UNKNOWN_INTERFACE = "org.freedesktop.DBus.Error.UnknownInterface"
    private const val ERROR_UNKNOWN_METHOD = "org.freedesktop.DBus.Error.UnknownMethod"
    private const val ERROR_FAILED = "org.freedesktop.DBus.Error.Failed"

    private val standardInterfaces = setOf(
        PROPERTIES_INTERFACE,
        OBJECT_MANAGER_INTERFACE,
        INTROSPECTABLE_INTERFACE,
        PEER_INTERFACE
    )

    private val machineId: String by lazy {
        sequenceOf("/etc/machine-id", "/var/lib/dbus/machine-id")
            .mapNotNull { runCatching { Files.readString(Paths.get(it)).trim() }.getOrNull() }
            .firstOrNull { it.isNotEmpty() }
            ?: "0".repeat(32)
    }

    /**
     * Serves [message] (addressed to one of [localUniqueName]'s exported objects) and sends the
     * reply on [wire]. Never throws to the caller: a handler exception becomes a D-Bus ERROR reply
     * so it cannot kill the reader/worker. Honors NO_REPLY_EXPECTED (no reply is sent).
     */
    fun handleIncomingCall(
        wire: DBusWireConnection,
        localUniqueName: String,
        message: WireMessage
    ) {
        val reply = runCatching { serve(localUniqueName, message) }.getOrElse { throwable ->
            errorReply(message, ERROR_FAILED, throwable.message ?: "Internal serving error")
        }
        if (message.flags and WireMessageFlags.NO_REPLY_EXPECTED != 0) return
        runCatching { wire.send(reply) }
    }

    private fun serve(localUniqueName: String, message: WireMessage): WireMessage {
        val member = message.member
            ?: return errorReply(message, ERROR_UNKNOWN_METHOD, "Missing member name")
        val iface = message.interfaceName
        return try {
            when (iface) {
                PEER_INTERFACE -> servePeer(message, member)
                INTROSPECTABLE_INTERFACE -> serveIntrospect(localUniqueName, message, member)
                else -> serveDispatch(localUniqueName, message, iface, member)
            }
        } catch (e: com.monkopedia.sdbus.Error) {
            errorReply(message, e.name, e.errorMessage)
        } catch (e: Throwable) {
            errorReply(message, ERROR_FAILED, e.message ?: "Handler failed")
        }
    }

    private fun servePeer(message: WireMessage, member: String): WireMessage = when (member) {
        "Ping" -> methodReturn(message, signature = null, values = emptyList())
        "GetMachineId" -> methodReturn(message, signature = "s", values = listOf(machineId))
        else -> errorReply(
            message,
            ERROR_UNKNOWN_METHOD,
            "Unknown Peer method $member"
        )
    }

    private fun serveIntrospect(
        localUniqueName: String,
        message: WireMessage,
        member: String
    ): WireMessage {
        if (member != "Introspect") {
            return errorReply(
                message,
                ERROR_UNKNOWN_METHOD,
                "Unknown Introspectable method $member"
            )
        }
        val path = message.path
            ?: return errorReply(message, ERROR_UNKNOWN_OBJECT, "Missing object path")
        val interfaces = WireServeRegistry.interfacesFor(localUniqueName, path)
        val children = WireServeRegistry.childNodeNames(localUniqueName, path)
        if (interfaces.isEmpty() && children.isEmpty()) {
            return errorReply(message, ERROR_UNKNOWN_OBJECT, "Unknown object $path")
        }
        val xml = introspectionXml(interfaces, children)
        return methodReturn(message, signature = "s", values = listOf(xml))
    }

    private fun serveDispatch(
        localUniqueName: String,
        message: WireMessage,
        iface: String?,
        member: String
    ): WireMessage {
        val path = message.path
            ?: return errorReply(message, ERROR_UNKNOWN_OBJECT, "Missing object path")
        val interfaceName = iface.orEmpty()
        val args = fromWireReplyValues(message.body, message.signature)

        if (!JvmStaticDispatch.hasHandler(path, interfaceName, member, args, localUniqueName)) {
            return classifyMissing(localUniqueName, message, iface, member)
        }

        val inbound = MethodCall().also {
            it.metadata = Message.Metadata(
                interfaceName = iface,
                memberName = member,
                sender = message.sender,
                destination = message.destination,
                path = path,
                valid = true,
                empty = args.isEmpty()
            )
            it.payload.addAll(args)
        }
        val result = JvmCurrentMessageContext.withMessage(inbound) {
            JvmStaticDispatch.invokeOrNull(
                objectPath = path,
                interfaceName = interfaceName,
                methodName = member,
                args = args,
                destination = localUniqueName
            )
        }
        return replyFor(message, iface, member, result)
    }

    private fun replyFor(
        message: WireMessage,
        iface: String?,
        member: String,
        result: Any?
    ): WireMessage = when {
        iface == PROPERTIES_INTERFACE && member == "Get" ->
            methodReturn(message, "v", listOf(toWireBodyValue(result)))

        iface == PROPERTIES_INTERFACE && member == "GetAll" ->
            methodReturn(message, "a{sv}", listOf(toWireBodyValue(result)))

        iface == PROPERTIES_INTERFACE && member == "Set" ->
            methodReturn(message, null, emptyList())

        iface == OBJECT_MANAGER_INTERFACE && member == "GetManagedObjects" ->
            methodReturn(message, "a{oa{sa{sv}}}", listOf(toWireBodyValue(result)))

        result is JvmStaticDispatch.DispatchResult -> {
            result.reply.error?.let { throw it }
            val signature = result.reply.declaredBodySignature.toString().ifEmpty { null }
            methodReturn(message, signature, result.reply.payload.map(::toWireBodyValue))
        }

        // A hasNoReply method (or any void result): reply with an empty body.
        else -> methodReturn(message, null, emptyList())
    }

    private fun classifyMissing(
        localUniqueName: String,
        message: WireMessage,
        iface: String?,
        member: String
    ): WireMessage {
        val path = message.path.orEmpty()
        val interfaces = WireServeRegistry.interfacesFor(localUniqueName, path)
        val servesPath = interfaces.isNotEmpty() ||
            WireServeRegistry.childNodeNames(localUniqueName, path).isNotEmpty()
        return when {
            !servesPath -> errorReply(message, ERROR_UNKNOWN_OBJECT, "Unknown object $path")
            iface != null && iface !in standardInterfaces && iface !in interfaces.keys ->
                errorReply(message, ERROR_UNKNOWN_INTERFACE, "Unknown interface $iface on $path")
            else -> errorReply(
                message,
                ERROR_UNKNOWN_METHOD,
                "Unknown method ${iface.orEmpty()}.$member on $path"
            )
        }
    }

    private fun methodReturn(
        call: WireMessage,
        signature: String?,
        values: List<Any?>
    ): WireMessage = WireMessage(
        type = WireMessageType.METHOD_RETURN,
        replySerial = call.serial,
        destination = call.sender,
        signature = signature?.takeUnless { it.isEmpty() },
        body = if (signature.isNullOrEmpty()) emptyList() else values
    )

    private fun errorReply(call: WireMessage, name: String, errorMessage: String): WireMessage =
        WireMessage(
            type = WireMessageType.ERROR,
            errorName = name,
            replySerial = call.serial,
            destination = call.sender,
            signature = "s",
            body = listOf(errorMessage)
        )

    // --- introspection XML ----------------------------------------------------------------------

    private fun introspectionXml(
        interfaces: Map<String, WireServeRegistry.InterfaceMeta>,
        children: List<String>
    ): String = buildString {
        append(
            "<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\"\n" +
                " \"http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd\">\n"
        )
        append("<node>\n")
        // Standard interfaces sd-bus always exposes for an exported object.
        append(STANDARD_INTROSPECTION)
        if (interfaces.values.any { it.objectManager }) append(OBJECT_MANAGER_INTROSPECTION)
        interfaces.toSortedMap().forEach { (name, meta) -> appendInterface(name, meta) }
        children.sorted().forEach { append("  <node name=\"$it\"/>\n") }
        append("</node>\n")
    }

    private fun StringBuilder.appendInterface(name: String, meta: WireServeRegistry.InterfaceMeta) {
        append("  <interface name=\"").append(name).append("\">\n")
        meta.methods.values.sortedBy { it.name }.forEach { method ->
            append("    <method name=\"").append(method.name).append("\">\n")
            splitTypes(method.inputSignature).forEachIndexed { i, type ->
                appendArg(type, method.inputNames.getOrNull(i), "in")
            }
            splitTypes(method.outputSignature).forEachIndexed { i, type ->
                appendArg(type, method.outputNames.getOrNull(i), "out")
            }
            if (method.deprecated) append(DEPRECATED_ANNOTATION)
            append("    </method>\n")
        }
        meta.signals.values.sortedBy { it.name }.forEach { signal ->
            append("    <signal name=\"").append(signal.name).append("\">\n")
            splitTypes(signal.signature).forEachIndexed { i, type ->
                appendArg(type, signal.paramNames.getOrNull(i), null)
            }
            if (signal.deprecated) append(DEPRECATED_ANNOTATION)
            append("    </signal>\n")
        }
        meta.properties.values.sortedBy { it.name }.forEach { property ->
            val access = when {
                property.readable && property.writable -> "readwrite"
                property.writable -> "write"
                else -> "read"
            }
            append("    <property name=\"").append(property.name)
                .append("\" type=\"").append(property.signature)
                .append("\" access=\"").append(access).append("\"")
            if (property.deprecated) {
                append(">\n").append("      ").append(DEPRECATED_ANNOTATION.trim()).append("\n")
                    .append("    </property>\n")
            } else {
                append("/>\n")
            }
        }
        append("  </interface>\n")
    }

    // Splits a signature into its top-level complete types (e.g. "ia{sv}" -> [i, a{sv}]); empty
    // signature -> empty list. Local to avoid colliding with the file-private helpers elsewhere.
    private fun splitTypes(signature: String): List<String> {
        if (signature.isEmpty()) return emptyList()
        fun parseOne(index: Int): Int {
            if (index >= signature.length) return index
            return when (signature[index]) {
                'a' -> parseOne(index + 1)
                '(' -> {
                    var i = index + 1
                    while (i < signature.length && signature[i] != ')') i = parseOne(i)
                    (i + 1).coerceAtMost(signature.length)
                }
                '{' -> {
                    var i = index + 1
                    while (i < signature.length && signature[i] != '}') i = parseOne(i)
                    (i + 1).coerceAtMost(signature.length)
                }
                else -> index + 1
            }
        }
        val types = mutableListOf<String>()
        var index = 0
        while (index < signature.length) {
            val next = parseOne(index)
            if (next <= index) break
            types += signature.substring(index, next)
            index = next
        }
        return types
    }

    private fun StringBuilder.appendArg(type: String, name: String?, direction: String?) {
        append("      <arg type=\"").append(type).append("\"")
        if (!name.isNullOrEmpty()) append(" name=\"").append(name).append("\"")
        if (direction != null) append(" direction=\"").append(direction).append("\"")
        append("/>\n")
    }

    private val DEPRECATED_ANNOTATION =
        "      <annotation name=\"org.freedesktop.DBus.Deprecated\" value=\"true\"/>\n"

    private val STANDARD_INTROSPECTION =
        "  <interface name=\"org.freedesktop.DBus.Peer\">\n" +
            "    <method name=\"Ping\"/>\n" +
            "    <method name=\"GetMachineId\">\n" +
            "      <arg type=\"s\" name=\"machine_uuid\" direction=\"out\"/>\n" +
            "    </method>\n" +
            "  </interface>\n" +
            "  <interface name=\"org.freedesktop.DBus.Introspectable\">\n" +
            "    <method name=\"Introspect\">\n" +
            "      <arg type=\"s\" name=\"xml_data\" direction=\"out\"/>\n" +
            "    </method>\n" +
            "  </interface>\n" +
            "  <interface name=\"org.freedesktop.DBus.Properties\">\n" +
            "    <method name=\"Get\">\n" +
            "      <arg type=\"s\" name=\"interface_name\" direction=\"in\"/>\n" +
            "      <arg type=\"s\" name=\"property_name\" direction=\"in\"/>\n" +
            "      <arg type=\"v\" name=\"value\" direction=\"out\"/>\n" +
            "    </method>\n" +
            "    <method name=\"Set\">\n" +
            "      <arg type=\"s\" name=\"interface_name\" direction=\"in\"/>\n" +
            "      <arg type=\"s\" name=\"property_name\" direction=\"in\"/>\n" +
            "      <arg type=\"v\" name=\"value\" direction=\"in\"/>\n" +
            "    </method>\n" +
            "    <method name=\"GetAll\">\n" +
            "      <arg type=\"s\" name=\"interface_name\" direction=\"in\"/>\n" +
            "      <arg type=\"a{sv}\" name=\"properties\" direction=\"out\"/>\n" +
            "    </method>\n" +
            "    <signal name=\"PropertiesChanged\">\n" +
            "      <arg type=\"s\" name=\"interface_name\"/>\n" +
            "      <arg type=\"a{sv}\" name=\"changed_properties\"/>\n" +
            "      <arg type=\"as\" name=\"invalidated_properties\"/>\n" +
            "    </signal>\n" +
            "  </interface>\n"

    private val OBJECT_MANAGER_INTROSPECTION =
        "  <interface name=\"org.freedesktop.DBus.ObjectManager\">\n" +
            "    <method name=\"GetManagedObjects\">\n" +
            "      <arg type=\"a{oa{sa{sv}}}\" name=\"objpath_interfaces_and_properties\" " +
            "direction=\"out\"/>\n" +
            "    </method>\n" +
            "    <signal name=\"InterfacesAdded\">\n" +
            "      <arg type=\"o\" name=\"object_path\"/>\n" +
            "      <arg type=\"a{sa{sv}}\" name=\"interfaces_and_properties\"/>\n" +
            "    </signal>\n" +
            "    <signal name=\"InterfacesRemoved\">\n" +
            "      <arg type=\"o\" name=\"object_path\"/>\n" +
            "      <arg type=\"as\" name=\"interfaces\"/>\n" +
            "    </signal>\n" +
            "  </interface>\n"
}

/**
 * Per-(connection unique name, object path) metadata for objects exported on the wire backend,
 * captured from the vtable so [WireServe] can route/introspect them. Keyed by the same destination
 * (the owning connection's unique name) the in-process [JvmStaticDispatch] handlers use.
 */
internal object WireServeRegistry {
    data class MethodMeta(
        val name: String,
        val inputSignature: String,
        val outputSignature: String,
        val inputNames: List<String>,
        val outputNames: List<String>,
        val deprecated: Boolean
    )

    data class PropertyMeta(
        val name: String,
        val signature: String,
        val readable: Boolean,
        val writable: Boolean,
        val deprecated: Boolean
    )

    data class SignalMeta(
        val name: String,
        val signature: String,
        val paramNames: List<String>,
        val deprecated: Boolean
    )

    data class InterfaceMeta(
        val methods: Map<String, MethodMeta>,
        val properties: Map<String, PropertyMeta>,
        val signals: Map<String, SignalMeta>,
        val objectManager: Boolean = false
    )

    private data class ObjectKey(val destination: String, val path: String)
    private data class Entry(var refCount: Int, val meta: InterfaceMeta)

    private val objects = mutableMapOf<ObjectKey, MutableMap<String, Entry>>()

    /** Captures the [vtable] metadata for [interfaceName] at ([destination], [path]). */
    fun registerVTable(
        destination: String,
        path: String,
        interfaceName: String,
        vtable: List<VTableItem>
    ): Resource {
        val meta = buildMeta(vtable)
        val key = ObjectKey(destination, path)
        synchronized(this) {
            val perObject = objects.getOrPut(key) { mutableMapOf() }
            val existing = perObject[interfaceName]
            if (existing != null) {
                existing.refCount++
            } else {
                perObject[interfaceName] = Entry(1, meta)
            }
        }
        return ActionResource {
            synchronized(this) {
                val perObject = objects[key] ?: return@ActionResource
                val entry = perObject[interfaceName] ?: return@ActionResource
                if (entry.refCount <= 1) {
                    perObject.remove(interfaceName)
                    if (perObject.isEmpty()) objects.remove(key)
                } else {
                    entry.refCount--
                }
            }
        }
    }

    /** Marks ([destination], [path]) as exposing the ObjectManager interface (for introspection). */
    fun registerObjectManager(destination: String, path: String): Resource {
        val key = ObjectKey(destination, path)
        val name = WireServe.OBJECT_MANAGER_INTERFACE
        synchronized(this) {
            val perObject = objects.getOrPut(key) { mutableMapOf() }
            val existing = perObject[name]
            if (existing != null) {
                existing.refCount++
            } else {
                perObject[name] = Entry(1, InterfaceMeta(emptyMap(), emptyMap(), emptyMap(), true))
            }
        }
        return ActionResource {
            synchronized(this) {
                val perObject = objects[key] ?: return@ActionResource
                val entry = perObject[name] ?: return@ActionResource
                if (entry.refCount <= 1) {
                    perObject.remove(name)
                    if (perObject.isEmpty()) objects.remove(key)
                } else {
                    entry.refCount--
                }
            }
        }
    }

    fun interfacesFor(destination: String, path: String): Map<String, InterfaceMeta> =
        synchronized(this) {
            objects[ObjectKey(destination, path)]
                ?.mapValues { it.value.meta }
                ?.toMap()
                .orEmpty()
        }

    /** Immediate child node segment names of [path] among this destination's exported paths. */
    fun childNodeNames(destination: String, path: String): List<String> = synchronized(this) {
        val prefix = if (path == "/") "/" else "$path/"
        objects.keys.asSequence()
            .filter { it.destination == destination && it.path != path }
            .mapNotNull { key ->
                if (!key.path.startsWith(prefix)) return@mapNotNull null
                key.path.substring(prefix.length).substringBefore('/').ifEmpty { null }
            }
            .distinct()
            .toList()
    }

    private fun buildMeta(vtable: List<VTableItem>): InterfaceMeta {
        val methods = mutableMapOf<String, MethodMeta>()
        val properties = mutableMapOf<String, PropertyMeta>()
        val signals = mutableMapOf<String, SignalMeta>()
        vtable.forEach { item ->
            when (item) {
                is MethodVTableItem -> methods[item.name.value] = MethodMeta(
                    name = item.name.value,
                    inputSignature = item.inputSignature?.value.orEmpty(),
                    outputSignature = item.outputSignature?.value.orEmpty(),
                    inputNames = item.inputParamNames,
                    outputNames = item.outputParamNames,
                    deprecated = item.isDeprecated
                )

                is PropertyVTableItem -> properties[item.name.value] = PropertyMeta(
                    name = item.name.value,
                    signature = item.signature?.value.orEmpty(),
                    readable = item.getter != null,
                    writable = item.setter != null,
                    deprecated = item.isDeprecated
                )

                is SignalVTableItem -> signals[item.name.value] = SignalMeta(
                    name = item.name.value,
                    signature = item.signature.value,
                    paramNames = item.paramNames,
                    deprecated = item.isDeprecated
                )

                else -> Unit
            }
        }
        return InterfaceMeta(methods, properties, signals)
    }
}
