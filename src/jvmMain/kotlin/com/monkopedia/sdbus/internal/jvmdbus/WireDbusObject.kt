package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.ActionResource
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.MethodVTableItem
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyGetReply
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.PropertySetCall
import com.monkopedia.sdbus.PropertyVTableItem
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.Signal
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.VTableItem
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.createError
import com.monkopedia.sdbus.signalFromMetadata
import java.util.concurrent.atomic.AtomicLong

private const val OBJECT_MANAGER_INTERFACE_NAME = "org.freedesktop.DBus.ObjectManager"
private const val OBJECT_MANAGER_GET_MANAGED_OBJECTS = "GetManagedObjects"

/**
 * Sends a signal OVER THE WIRE on the self-owned connection (epic #93). Supplied to
 * [WireDbusObject] by [WireDbusBackend], so an object served on the owned connection emits a real
 * D-Bus SIGNAL that the bus routes to every subscriber — external processes AND same-JVM
 * connections (each of which has its own socket + `AddMatch`). The sender header is left unset:
 * the bus stamps the authoritative sender (our unique name) and attaches the sender's credentials.
 */
internal fun interface WireSignalEmitter {
    fun emitSignalOverWire(
        path: String,
        interfaceName: String,
        member: String,
        signature: String?,
        payload: List<Any?>
    )
}

/**
 * An object exported on the owned wire connection. Method dispatch and managed-object/property
 * bookkeeping live in the shared in-process registries ([JvmStaticDispatch],
 * [LocalManagedObjectsRegistry], [LocalObjectManagerRegistry]) so a call to a same-process peer is
 * serviced locally; [WireServeRegistry] captures the vtable metadata so the SAME registration is
 * reachable by EXTERNAL callers over the bus (see [WireServe]). Signal EMISSION goes over the wire
 * via [wireSignalEmitter], uniform with the native backend.
 */
internal class WireDbusObject(
    private val objectPath: ObjectPath,
    private val senderName: String?,
    private val wireSignalEmitter: WireSignalEmitter
) : JvmDbusObject {

    private val registrations = mutableListOf<Resource>()
    private val registeredInterfaces = mutableMapOf<String, Int>()
    private val asyncReplyTimeoutMs = 10_000L
    private val propertiesInterfaceName = "org.freedesktop.DBus.Properties"
    private val propertiesChangedMemberName = "PropertiesChanged"
    private val propertiesChangedSignature = "sa{sv}as"
    private val objectManagerInterfaceName = OBJECT_MANAGER_INTERFACE_NAME
    private val interfacesAddedMemberName = "InterfacesAdded"
    private val interfacesRemovedMemberName = "InterfacesRemoved"
    private val interfacesAddedSignature = "oa{sa{sv}}"
    private val interfacesRemovedSignature = "oas"
    private val propertiesByInterface =
        mutableMapOf<String, MutableMap<String, PropertyVTableItem>>()
    private var propertiesDispatchRegistered = false
    private val dispatchDestination = senderName.orEmpty()

    private fun parseInterfaceName(value: Any?): String = when (value) {
        is InterfaceName -> value.value
        is String -> value
        else -> throw createError(-1, "Properties call failed: invalid interface argument")
    }

    private fun parsePropertyName(value: Any?): String = when (value) {
        is PropertyName -> value.value
        is String -> value
        else -> throw createError(-1, "Properties call failed: invalid property argument")
    }

    private fun propertyToVariant(value: Any?): Variant = when (value) {
        is Variant -> value
        is Boolean -> Variant(value)
        is Byte -> Variant(value.toUByte())
        is UByte -> Variant(value)
        is Short -> Variant(value)
        is UShort -> Variant(value)
        is Int -> Variant(value)
        is UInt -> Variant(value)
        is Long -> Variant(value)
        is ULong -> Variant(value)
        is Float -> Variant(value.toDouble())
        is Double -> Variant(value)
        is String -> Variant(value)
        is ObjectPath -> Variant(value)
        is Signature -> Variant(value)
        is UnixFd -> Variant(value)
        else -> throw createError(
            -1,
            "Properties call failed: unsupported property value type ${value?.let {
                it::class.simpleName
            }}"
        )
    }

    private fun variantToPropertyValue(variant: Variant): Any = when (variant.peekValueType()) {
        "b" -> variant.get<Boolean>()
        "y" -> variant.get<UByte>()
        "n" -> variant.get<Short>()
        "q" -> variant.get<UShort>()
        "i" -> variant.get<Int>()
        "u" -> variant.get<UInt>()
        "x" -> variant.get<Long>()
        "t" -> variant.get<ULong>()
        "d" -> variant.get<Double>()
        "s" -> variant.get<String>()
        "o" -> variant.get<ObjectPath>()
        "g" -> variant.get<Signature>()
        "h" -> variant.get<UnixFd>()
        else -> throw createError(-1, "Properties call failed: unsupported variant value type")
    }

    private fun ensurePropertiesDispatchRegistered() {
        if (propertiesDispatchRegistered) return
        JvmStaticDispatch.register(
            destination = dispatchDestination,
            objectPath = objectPath.value,
            interfaceName = propertiesInterfaceName,
            methodName = "Get",
            argCount = 2
        ) { args ->
            val iface = parseInterfaceName(args.getOrNull(0))
            val prop = parsePropertyName(args.getOrNull(1))
            val property = propertiesByInterface[iface]?.get(prop)
                ?: throw createError(-1, "Properties.Get failed: unknown $iface.$prop")
            val reply = PropertyGetReply()
            JvmCurrentMessageContext.withMessage(reply) {
                property.getter?.invoke(reply)
                    ?: throw createError(-1, "Properties.Get failed: no getter for $iface.$prop")
            }
            propertyToVariant(reply.payload.firstOrNull())
        }
        JvmStaticDispatch.register(
            destination = dispatchDestination,
            objectPath = objectPath.value,
            interfaceName = propertiesInterfaceName,
            methodName = "Set",
            argCount = 3
        ) { args ->
            val iface = parseInterfaceName(args.getOrNull(0))
            val prop = parsePropertyName(args.getOrNull(1))
            val property = propertiesByInterface[iface]?.get(prop)
                ?: throw createError(-1, "Properties.Set failed: unknown $iface.$prop")
            val value = args.getOrNull(2)
            val setValue = if (value is Variant) variantToPropertyValue(value) else value
            val call = PropertySetCall()
            val inbound = JvmCurrentMessageContext.current()
            call.metadata = Message.Metadata(
                interfaceName = propertiesInterfaceName,
                memberName = "Set",
                sender = inbound?.sender?.value,
                destination = inbound?.destination?.value,
                path = objectPath.value,
                valid = true,
                empty = false
            )
            call.payload.add(setValue)
            JvmCurrentMessageContext.withMessage(call) {
                property.setter?.invoke(call)
                    ?: throw createError(-1, "Properties.Set failed: no setter for $iface.$prop")
            }
            Unit
        }
        JvmStaticDispatch.register(
            destination = dispatchDestination,
            objectPath = objectPath.value,
            interfaceName = propertiesInterfaceName,
            methodName = "GetAll",
            argCount = 1
        ) { args ->
            val iface = parseInterfaceName(args.getOrNull(0))
            val props = propertiesByInterface[iface].orEmpty()
            props.entries.associate { (name, property) ->
                val reply = PropertyGetReply()
                JvmCurrentMessageContext.withMessage(reply) {
                    property.getter?.invoke(reply)
                        ?: throw createError(
                            -1,
                            "Properties.GetAll failed: no getter for $iface.$name"
                        )
                }
                PropertyName(name) to propertyToVariant(reply.payload.firstOrNull())
            }
        }
        propertiesDispatchRegistered = true
    }

    private fun snapshotInterfaceProperties(interfaceName: String): Map<PropertyName, Variant> {
        val properties = propertiesByInterface[interfaceName].orEmpty()
        if (properties.isEmpty()) return emptyMap()
        return properties.entries.mapNotNull { (name, property) ->
            val getter = property.getter ?: return@mapNotNull null
            val reply = PropertyGetReply()
            val value = JvmCurrentMessageContext.withMessage(reply) {
                getter(reply)
                reply.payload.firstOrNull()
            }
            PropertyName(name) to propertyToVariant(value)
        }.toMap()
    }

    private fun readPropertyValue(property: PropertyVTableItem): Variant {
        val getter = property.getter ?: error("no getter")
        val reply = PropertyGetReply()
        val value = JvmCurrentMessageContext.withMessage(reply) {
            getter(reply)
            reply.payload.firstOrNull()
        }
        return propertyToVariant(value)
    }

    // Mirrors native's sd_bus_emit_properties_changed_strv: each named property whose vtable
    // getter can be read goes into changed_properties with its current value; names without a
    // readable getter (write-only / value unavailable) fall back to invalidated_properties.
    private fun resolveChangedProperties(
        interfaceName: String,
        propNames: List<PropertyName>
    ): Pair<Map<PropertyName, Variant>, List<PropertyName>> {
        val properties = propertiesByInterface[interfaceName].orEmpty()
        val changed = mutableMapOf<PropertyName, Variant>()
        val invalidated = mutableListOf<PropertyName>()
        propNames.forEach { propName ->
            val property = properties[propName.value]
            val value = property
                ?.takeIf { it.getter != null }
                ?.let { runCatching { readPropertyValue(it) }.getOrNull() }
            if (value != null) {
                changed[propName] = value
            } else {
                invalidated += propName
            }
        }
        return changed to invalidated
    }

    override fun emitPropertiesChangedSignal(
        interfaceName: InterfaceName,
        propNames: List<PropertyName>
    ) {
        val (changedProperties, invalidatedProperties) =
            resolveChangedProperties(interfaceName.value, propNames)
        val payload = listOf(interfaceName, changedProperties, invalidatedProperties)
        wireSignalEmitter.emitSignalOverWire(
            path = objectPath.value,
            interfaceName = propertiesInterfaceName,
            member = propertiesChangedMemberName,
            signature = propertiesChangedSignature,
            payload = payload
        )
    }

    override fun emitPropertiesChangedSignal(interfaceName: InterfaceName) {
        emitPropertiesChangedSignal(interfaceName, emptyList())
    }

    override fun emitInterfacesAddedSignal() {
        emitInterfacesAddedSignal(registeredInterfaces.keys.map(::InterfaceName))
    }

    override fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>) {
        val signalPath = LocalObjectManagerRegistry.resolveFor(objectPath.value) ?: objectPath.value
        val payload = listOf(
            objectPath,
            interfaces.associateWith { emptyMap<PropertyName, Variant>() }
        )
        wireSignalEmitter.emitSignalOverWire(
            path = signalPath,
            interfaceName = objectManagerInterfaceName,
            member = interfacesAddedMemberName,
            signature = interfacesAddedSignature,
            payload = payload
        )
    }

    override fun emitInterfacesRemovedSignal() {
        emitInterfacesRemovedSignal(registeredInterfaces.keys.map(::InterfaceName))
    }

    override fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>) {
        val signalPath = LocalObjectManagerRegistry.resolveFor(objectPath.value) ?: objectPath.value
        val payload = listOf(objectPath, interfaces)
        wireSignalEmitter.emitSignalOverWire(
            path = signalPath,
            interfaceName = objectManagerInterfaceName,
            member = interfacesRemovedMemberName,
            signature = interfacesRemovedSignature,
            payload = payload
        )
    }

    override fun addObjectManager(): Resource {
        val local = LocalObjectManagerRegistry.register(objectPath.value, dispatchDestination)
        registrations += local
        // Advertise ObjectManager for over-the-wire introspection/serving too.
        val wireResource = WireServeRegistry.registerObjectManager(
            dispatchDestination,
            objectPath.value
        )
            .also { registrations += it }
        return ActionResource {
            local.release()
            wireResource.release()
            registrations.remove(local)
            registrations.remove(wireResource)
        }
    }

    override fun currentlyProcessedMessage(): Message =
        JvmCurrentMessageContext.current() ?: signalFromMetadata(Message.Metadata())

    override fun addVTable(interfaceName: InterfaceName, vtable: List<VTableItem>): Resource {
        registeredInterfaces[interfaceName.value] =
            (registeredInterfaces[interfaceName.value] ?: 0) + 1
        val interfaceProperties = propertiesByInterface.getOrPut(interfaceName.value) {
            mutableMapOf()
        }
        val addedPropertyNames = mutableListOf<String>()
        vtable.forEach { item ->
            val property = item as? PropertyVTableItem ?: return@forEach
            interfaceProperties[property.name.value] = property
            addedPropertyNames += property.name.value
        }
        if (addedPropertyNames.isNotEmpty()) {
            ensurePropertiesDispatchRegistered()
        }
        val resources = mutableListOf<Resource>()
        resources += LocalManagedObjectsRegistry.registerInterface(
            destination = dispatchDestination,
            objectPath = objectPath.value,
            interfaceName = interfaceName.value
        ) {
            snapshotInterfaceProperties(interfaceName.value)
        }
        // Capture the vtable metadata so the owned connection can route, introspect and serve this
        // object to EXTERNAL callers over the bus, not just in-process (epic #93 phase 4).
        resources += WireServeRegistry.registerVTable(
            destination = dispatchDestination,
            path = objectPath.value,
            interfaceName = interfaceName.value,
            vtable = vtable
        )
        resources += vtable.mapNotNull { item ->
            val method = item as? MethodVTableItem ?: return@mapNotNull null
            val callback = method.callbackHandler ?: return@mapNotNull null
            val argCount = countTopLevelTypes(method.inputSignature?.value)
            JvmStaticDispatch.register(
                destination = dispatchDestination,
                objectPath = objectPath.value,
                interfaceName = interfaceName.value,
                methodName = method.name.value,
                argCount = argCount
            ) { args ->
                val call = MethodCall().also {
                    val inbound = JvmCurrentMessageContext.current()
                    it.metadata = Message.Metadata(
                        interfaceName = interfaceName.value,
                        memberName = method.name.value,
                        sender = inbound?.sender?.value,
                        destination = inbound?.destination?.value,
                        path = objectPath.value,
                        valid = true,
                        empty = args.isEmpty()
                    )
                    it.payload.addAll(args)
                }
                JvmCurrentMessageContext.withMessage(call) {
                    callback(call)
                }
                if (method.hasNoReply) return@register Unit
                val reply = call.sentReply ?: call.awaitReply(asyncReplyTimeoutMs)
                    ?: throw createError(
                        -1,
                        "No reply sent for ${interfaceName.value}.${method.name.value} within ${asyncReplyTimeoutMs}ms"
                    )
                JvmStaticDispatch.DispatchResult(reply)
            }
            ActionResource {
                JvmStaticDispatch.unregister(
                    destination = dispatchDestination,
                    objectPath = objectPath.value,
                    interfaceName = interfaceName.value,
                    methodName = method.name.value,
                    argCount = argCount
                )
            }
        }
        registrations.addAll(resources)
        return ActionResource {
            resources.forEach { it.release() }
            registrations.removeAll(resources)
            val current = registeredInterfaces[interfaceName.value] ?: 0
            if (current <= 1) {
                registeredInterfaces.remove(interfaceName.value)
            } else {
                registeredInterfaces[interfaceName.value] = current - 1
            }
            if (addedPropertyNames.isNotEmpty()) {
                val props = propertiesByInterface[interfaceName.value]
                addedPropertyNames.forEach { props?.remove(it) }
                if (props.isNullOrEmpty()) {
                    propertiesByInterface.remove(interfaceName.value)
                }
                // When no property interfaces remain, drop the shared Properties dispatch handlers
                // so this object (and its captured adaptor) is not pinned by the dispatch singleton.
                if (propertiesByInterface.isEmpty()) {
                    unregisterPropertiesDispatch()
                }
            }
        }
    }

    override fun createSignal(interfaceName: InterfaceName, signalName: SignalName): Signal =
        signalFromMetadata(
            Message.Metadata(
                interfaceName = interfaceName.value,
                memberName = signalName.value,
                sender = senderName,
                path = objectPath.value,
                valid = true,
                empty = true
            )
        ) { emitSignal(it) }

    override fun emitSignal(message: Signal) {
        val interfaceName = message.interfaceName?.value
            ?: throw createError(-1, "emitSignal failed: missing interface name")
        val signalName = message.memberName?.value
            ?: throw createError(-1, "emitSignal failed: missing signal name")
        val path = message.path?.value ?: objectPath.value
        // Prefer the declared signature (correct even for empty collections, which carry no
        // runtime element to infer from); fall back to value inference only when no usable
        // declared signature is available. Mirrors the method-call body path.
        val signature = bodySignature(
            message.payload.toList(),
            message.declaredBodySignature.toString()
        ) { "emitSignal failed: unsupported signal payload type" }
        wireSignalEmitter.emitSignalOverWire(
            path = path,
            interfaceName = interfaceName,
            member = signalName,
            signature = signature,
            payload = message.payload
        )
    }

    private fun unregisterPropertiesDispatch() {
        if (!propertiesDispatchRegistered) return
        // The Properties Get/Set/GetAll handlers live in the process-wide JvmStaticDispatch
        // singleton and each captures this WireDbusObject (and through it the user adaptor's
        // getters/setters and the wire connection). They must be removed on teardown or the
        // served object leaks for its whole process lifetime.
        unregisterPropertiesMethod("Get", 2)
        unregisterPropertiesMethod("Set", 3)
        unregisterPropertiesMethod("GetAll", 1)
        propertiesDispatchRegistered = false
    }

    private fun unregisterPropertiesMethod(methodName: String, argCount: Int) {
        JvmStaticDispatch.unregister(
            objectPath = objectPath.value,
            interfaceName = propertiesInterfaceName,
            methodName = methodName,
            argCount = argCount,
            destination = dispatchDestination
        )
    }

    override fun release(): Unit = run {
        registrations.forEach { it.release() }
        registrations.clear()
        unregisterPropertiesDispatch()
        propertiesByInterface.clear()
        registeredInterfaces.clear()
    }
}

// --- shared in-process registries (used by both the client path and wire serving) ------------

internal object LocalManagedObjectsRegistry {
    private data class ManagedInterfaceEntry(
        val providers: MutableMap<Long, () -> Map<PropertyName, Variant>> = mutableMapOf()
    )

    private data class ManagedObjectKey(
        val destination: String,
        val objectPath: String,
        val interfaceName: String
    )

    private val nextProviderId = AtomicLong(1)
    private val interfaces = mutableMapOf<ManagedObjectKey, ManagedInterfaceEntry>()

    fun registerInterface(
        destination: String,
        objectPath: String,
        interfaceName: String,
        propertiesProvider: () -> Map<PropertyName, Variant>
    ): Resource {
        val providerId = nextProviderId.getAndIncrement()
        val key = ManagedObjectKey(destination, objectPath, interfaceName)
        synchronized(this) {
            interfaces
                .getOrPut(key) { ManagedInterfaceEntry() }
                .providers[providerId] = propertiesProvider
        }
        return ActionResource {
            synchronized(this) {
                val entry = interfaces[key] ?: return@synchronized
                entry.providers.remove(providerId)
                if (entry.providers.isEmpty()) {
                    interfaces.remove(key)
                }
            }
        }
    }

    fun managedObjectsFor(
        managerPath: String,
        destination: String
    ): Map<ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>> = synchronized(this) {
        val snapshot =
            mutableMapOf<ObjectPath, MutableMap<InterfaceName, Map<PropertyName, Variant>>>()
        interfaces.entries
            .asSequence()
            .filter { (key, _) ->
                key.destination == destination &&
                    key.objectPath.startsWith("$managerPath/") &&
                    key.objectPath != managerPath
            }
            .forEach { (key, entry) ->
                val objectPath = ObjectPath(key.objectPath)
                val interfaceName = InterfaceName(key.interfaceName)
                val properties = entry.providers.values
                    .fold(emptyMap<PropertyName, Variant>()) { acc, provider ->
                        acc + provider()
                    }
                snapshot.getOrPut(objectPath) { mutableMapOf() }[interfaceName] = properties
            }
        snapshot.mapValues { (_, interfacesForObject) -> interfacesForObject.toMap() }
    }
}

internal object LocalObjectManagerRegistry {
    private data class ManagerKey(val destination: String, val path: String)

    private val managerPaths = mutableMapOf<String, Int>()
    private val dispatchRegistrations = mutableMapOf<ManagerKey, Int>()

    fun register(path: String, destination: String): Resource {
        val key = ManagerKey(destination, path)
        synchronized(this) {
            managerPaths[path] = (managerPaths[path] ?: 0) + 1
            val currentDispatchRefs = dispatchRegistrations[key] ?: 0
            dispatchRegistrations[key] = currentDispatchRefs + 1
            if (currentDispatchRefs == 0) {
                JvmStaticDispatch.register(
                    destination = destination,
                    objectPath = path,
                    interfaceName = OBJECT_MANAGER_INTERFACE_NAME,
                    methodName = OBJECT_MANAGER_GET_MANAGED_OBJECTS,
                    argCount = 0
                ) {
                    LocalManagedObjectsRegistry.managedObjectsFor(path, destination)
                }
            }
        }
        return ActionResource {
            synchronized(this) {
                val current = managerPaths[path] ?: return@synchronized
                if (current <= 1) {
                    managerPaths.remove(path)
                } else {
                    managerPaths[path] = current - 1
                }

                val currentDispatchRefs = dispatchRegistrations[key] ?: return@synchronized
                if (currentDispatchRefs <= 1) {
                    dispatchRegistrations.remove(key)
                    JvmStaticDispatch.unregister(
                        destination = destination,
                        objectPath = path,
                        interfaceName = OBJECT_MANAGER_INTERFACE_NAME,
                        methodName = OBJECT_MANAGER_GET_MANAGED_OBJECTS,
                        argCount = 0
                    )
                } else {
                    dispatchRegistrations[key] = currentDispatchRefs - 1
                }
            }
        }
    }

    fun resolveFor(objectPath: String): String? = synchronized(this) {
        managerPaths.keys
            .filter { manager ->
                objectPath == manager || objectPath.startsWith("$manager/")
            }
            .maxByOrNull { it.length }
    }
}

internal object LocalJvmServiceRegistry {
    private val namesByUnique = mutableMapOf<String, MutableSet<String>>()

    fun registerLocalUniqueName(uniqueName: String) {
        if (uniqueName.isEmpty()) return
        synchronized(this) {
            namesByUnique.getOrPut(uniqueName) { mutableSetOf() } += uniqueName
        }
    }

    fun unregisterLocalUniqueName(uniqueName: String) {
        if (uniqueName.isEmpty()) return
        synchronized(this) {
            namesByUnique.remove(uniqueName)
        }
    }

    fun addAlias(uniqueName: String, alias: String) {
        if (uniqueName.isEmpty() || alias.isEmpty()) return
        synchronized(this) {
            namesByUnique.getOrPut(uniqueName) { mutableSetOf() }.apply {
                add(uniqueName)
                add(alias)
            }
        }
    }

    fun removeAlias(uniqueName: String, alias: String) {
        if (uniqueName.isEmpty() || alias.isEmpty()) return
        synchronized(this) {
            val names = namesByUnique[uniqueName] ?: return@synchronized
            if (alias != uniqueName) {
                names.remove(alias)
            }
            if (names.isEmpty()) {
                namesByUnique.remove(uniqueName)
            }
        }
    }

    fun resolveLocalUniqueName(destination: String): String? {
        if (destination.isEmpty()) return null
        synchronized(this) {
            if (namesByUnique.containsKey(destination)) {
                return destination
            }
            return namesByUnique.entries
                .firstOrNull { (_, names) -> destination in names }
                ?.key
        }
    }
}

// --- signature helpers (outgoing signal body) ------------------------------------------------

// Splits a D-Bus signature into its top-level single complete types
// (e.g. "sa{sv}(is)" -> ["s", "a{sv}", "(is)"]).
private fun splitTopLevelTypes(signature: String): List<String> {
    fun parseOne(index: Int): Int {
        if (index >= signature.length) return index
        return when (signature[index]) {
            'a' -> parseOne(index + 1)
            '(' -> {
                var i = index + 1
                while (i < signature.length && signature[i] != ')') {
                    i = parseOne(i)
                }
                (i + 1).coerceAtMost(signature.length)
            }
            '{' -> {
                var i = index + 1
                while (i < signature.length && signature[i] != '}') {
                    i = parseOne(i)
                }
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

// Counts the number of top-level D-Bus types in a signature (e.g. "sa{sv}" -> 2).
private fun countTopLevelTypes(signature: String?): Int =
    if (signature.isNullOrEmpty()) 0 else splitTopLevelTypes(signature).size

// Wire signature for an outgoing signal body. Prefer the declared signature accumulated from
// serializer descriptors (correct even for empty collections); only fall back to value-based
// inference when no usable declared signature is available. The top-level type count guards
// against a declared signature that doesn't line up with the payload.
private fun bodySignature(
    payload: List<Any?>,
    declaredSignature: String?,
    errorFor: (Any?) -> String
): String {
    if (!declaredSignature.isNullOrEmpty() &&
        countTopLevelTypes(declaredSignature) == payload.size
    ) {
        return declaredSignature
    }
    return payload.joinToString(separator = "") { value ->
        inferSignalSignature(value) ?: throw createError(-1, errorFor(value))
    }
}

private fun inferSignalSignature(value: Any?): String? = when (value) {
    null -> null
    is Message.JvmVariantPayload -> "v"
    is Message.JvmStructPayload -> value.signature
    is Variant -> "v"
    is Boolean -> "b"
    is Byte, is UByte -> "y"
    is Short -> "n"
    is UShort -> "q"
    is Int -> "i"
    is UInt -> "u"
    is Long -> "x"
    is ULong -> "t"
    is Float, is Double -> "d"
    is String -> "s"
    is com.monkopedia.sdbus.BusName,
    is InterfaceName,
    is com.monkopedia.sdbus.MemberName -> "s"
    is ObjectPath -> "o"
    is Signature -> "g"
    is UnixFd -> "h"
    is List<*> -> {
        val elementSig = value.firstNotNullOfOrNull(::inferSignalSignature) ?: return "a"
        "a$elementSig"
    }

    is Array<*> -> {
        val elementSig = value.firstNotNullOfOrNull(::inferSignalSignature) ?: return "a"
        "a$elementSig"
    }

    is Map<*, *> -> {
        val first = value.entries.firstOrNull() ?: return "a{}"
        val keySig = inferSignalSignature(first.key) ?: return "a{}"
        val valueSig = inferSignalSignature(first.value) ?: return "a{}"
        "a{$keySig$valueSig}"
    }

    else -> null
}
