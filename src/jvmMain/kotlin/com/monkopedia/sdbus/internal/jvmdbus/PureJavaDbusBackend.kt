package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.ActionResource
import com.monkopedia.sdbus.BusName
import com.monkopedia.sdbus.CloseableResource
import com.monkopedia.sdbus.Connection
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.JvmConnection
import com.monkopedia.sdbus.MemberName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MessageHandler
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MethodReply
import com.monkopedia.sdbus.MethodVTableItem
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PendingAsyncCall
import com.monkopedia.sdbus.PlainMessage
import com.monkopedia.sdbus.PropertyGetReply
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.PropertySetCall
import com.monkopedia.sdbus.PropertyVTableItem
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Signal
import com.monkopedia.sdbus.SignalHandler
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.VTableItem
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.createError
import com.monkopedia.sdbus.methodReplyFrom
import com.monkopedia.sdbus.signalFromMetadata
import com.sun.security.auth.module.UnixSystem
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.time.Duration
import org.freedesktop.dbus.DBusMatchRule
import org.freedesktop.dbus.FileDescriptor
import org.freedesktop.dbus.connections.AbstractConnection
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.connections.impl.DirectConnectionBuilder
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.DBusSigHandler
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt16
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.UInt64

private const val OBJECT_MANAGER_INTERFACE_NAME = "org.freedesktop.DBus.ObjectManager"
private const val OBJECT_MANAGER_GET_MANAGED_OBJECTS = "GetManagedObjects"

internal class PureJavaDbusBackend(private val fallbackBackend: JvmDbusBackend) : JvmDbusBackend {
    override fun createConnection(
        busType: JvmBusType,
        endpoint: String?,
        name: ServiceName?,
        fd: Int?
    ): JvmDbusConnection {
        if (busType == JvmBusType.DIRECT_FD) {
            throw createError(
                -1,
                "JVM backend does not support createDirectBusConnection(fd)"
            )
        }
        if (busType == JvmBusType.SERVER_FD) {
            throw createError(
                -1,
                "JVM backend does not support createServerBus(fd)"
            )
        }
        val connection = runCatching {
            tryCreateConnection(busType, endpoint, fd)
        }.getOrElse {
            if (busType == JvmBusType.DIRECT_ADDRESS && endpoint != null) {
                throw createError(
                    -1,
                    "Failed to create direct JVM D-Bus connection for '$endpoint': ${it.message}"
                )
            }
            null
        } ?: return fallbackBackend.createConnection(busType, endpoint, name, fd)
        return PureJavaDbusConnection(connection).also { conn ->
            if (name != null) {
                conn.requestName(name)
            }
        }
    }

    override fun createProxy(
        connection: Connection,
        destination: ServiceName,
        objectPath: ObjectPath,
        dontRunEventLoopThread: Boolean
    ): JvmDbusProxy {
        if (!dontRunEventLoopThread) {
            connection.enterEventLoopAsync()
        }
        val javaConnection = (connection as? JvmConnection)?.backend as? PureJavaDbusConnection
        return PureJavaDbusProxy(
            javaConnection?.javaConnection,
            destination,
            objectPath,
            runCatching { connection.getUniqueName().value }.getOrNull()
        )
    }

    override fun createObject(connection: Connection, objectPath: ObjectPath): JvmDbusObject =
        PureJavaDbusObject(
            objectPath,
            (connection as? JvmConnection)?.backend
                .let { it as? PureJavaDbusConnection }
                ?.javaConnection,
            runCatching { connection.getUniqueName().value }.getOrNull()
        )

    private fun tryCreateConnection(
        busType: JvmBusType,
        endpoint: String?,
        fd: Int?
    ): AbstractConnection? {
        if (fd != null) return null
        return when (busType) {
            JvmBusType.DEFAULT,
            JvmBusType.SESSION -> DBusConnectionBuilder.forSessionBus().withShared(false).build()
            JvmBusType.SYSTEM -> DBusConnectionBuilder.forSystemBus().withShared(false).build()
            JvmBusType.SESSION_ADDRESS ->
                endpoint?.let(DBusConnectionBuilder::forAddress)?.withShared(false)?.build()
            JvmBusType.DIRECT_ADDRESS -> endpoint?.let(DirectConnectionBuilder::forAddress)?.build()
            JvmBusType.REMOTE_SYSTEM -> endpoint?.let { value ->
                DBusConnectionBuilder.forType(DBusConnection.DBusBusType.SYSTEM, value)
                    .withShared(false)
                    .build()
            }
            JvmBusType.DIRECT_FD,
            JvmBusType.SERVER_FD -> null
        }
    }
}

private data class MatchFilter(
    val sender: String? = null,
    val path: String? = null,
    val interfaceName: String? = null,
    val member: String? = null
)

private object LocalJvmSignalBus {
    data class Key(
        val destination: String,
        val path: String,
        val interfaceName: String,
        val member: String
    )

    private val nextId = AtomicLong(1)
    private val handlers = mutableMapOf<Key, MutableMap<Long, SignalHandler>>()

    fun register(
        destination: String,
        path: String,
        interfaceName: String,
        member: String,
        signalHandler: SignalHandler
    ): Resource {
        val id = nextId.getAndIncrement()
        val key = Key(destination, path, interfaceName, member)
        synchronized(this) {
            val perKey = handlers.getOrPut(key) { mutableMapOf() }
            perKey[id] = signalHandler
        }
        return ActionResource {
            synchronized(this) {
                val perKey = handlers[key] ?: return@synchronized
                perKey.remove(id)
                if (perKey.isEmpty()) {
                    handlers.remove(key)
                }
            }
        }
    }

    fun emit(
        sender: String?,
        path: String,
        interfaceName: String,
        member: String,
        payload: List<Any?>
    ) {
        LocalJvmMatchBus.emit(sender, path, interfaceName, member, payload)
        val message = signalFromMetadata(
            Message.Metadata(
                interfaceName = interfaceName,
                memberName = member,
                sender = sender,
                path = path,
                valid = true,
                empty = payload.isEmpty()
            )
        ).also {
            it.payload.addAll(payload)
        }
        val callbacks = synchronized(this) {
            handlers.filterKeys { key ->
                key.path == path &&
                    key.interfaceName == interfaceName &&
                    key.member == member &&
                    (key.destination.isEmpty() || key.destination == sender)
            }.values.flatMap { it.values }.toList()
        }
        callbacks.forEach { callback ->
            JvmCurrentMessageContext.withMessage(message) {
                callback(message)
            }
        }
    }
}

private object LocalManagedObjectsRegistry {
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

private object LocalObjectManagerRegistry {
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

private object LocalJvmServiceRegistry {
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

private data class SenderCredentials(
    val pid: Int?,
    val uid: UInt?,
    val gid: UInt?,
    val supplementaryGids: List<UInt>?,
    val selinuxContext: String?
)

private val localProcessCredentials: SenderCredentials by lazy {
    val pid = runCatching { ProcessHandle.current().pid().toInt() }.getOrNull()
    val unix = runCatching { UnixSystem() }.getOrNull()
    val uid = unix?.uid?.toUInt()
    val gid = unix?.gid?.toUInt()
    val groups = unix?.groups?.map { it.toUInt() }
    val selinuxContext = runCatching {
        Files.readString(Paths.get("/proc/self/attr/current"))
            .trim('\u0000', ' ')
            .ifEmpty { null }
    }.getOrNull()
    SenderCredentials(
        pid = pid,
        uid = uid,
        gid = gid,
        supplementaryGids = groups,
        selinuxContext = selinuxContext
    )
}

private fun localProcessCredentialsOrNull(): SenderCredentials? = localProcessCredentials.takeIf {
    it.pid != null ||
        it.uid != null ||
        it.gid != null ||
        it.supplementaryGids != null ||
        it.selinuxContext != null
}

private fun credentialUInt(value: Any?): UInt? = when (value) {
    is UInt -> value
    is Number -> value.toLong().takeIf { it >= 0L }?.toUInt()
    is String -> value.toULongOrNull()?.toUInt()
    else -> null
}

private fun credentialUIntList(value: Any?): List<UInt>? = when (value) {
    is List<*> -> value.mapNotNull(::credentialUInt).takeIf { it.isNotEmpty() }
    is Array<*> -> value.mapNotNull(::credentialUInt).takeIf { it.isNotEmpty() }
    else -> null
}

private fun credentialBytes(value: Any?): ByteArray? = when (value) {
    is ByteArray -> value
    is List<*> -> value.mapNotNull { (it as? Number)?.toByte() }.toByteArray()
        .takeIf { it.isNotEmpty() }
    is Array<*> -> value.mapNotNull { (it as? Number)?.toByte() }.toByteArray()
        .takeIf { it.isNotEmpty() }

    else -> null
}

private fun credentialSelinuxContext(value: Any?): String? = when (value) {
    is String -> value.trim().ifEmpty { null }
    else -> credentialBytes(value)
        ?.toString(Charsets.UTF_8)
        ?.trim('\u0000', ' ')
        ?.ifEmpty { null }
}

private fun firstCredentialValue(
    credentials: Map<String, org.freedesktop.dbus.types.Variant<*>>,
    keys: List<String>
): Any? = keys.firstNotNullOfOrNull { key -> credentials[key]?.value }

private fun resolveSenderCredentials(
    connection: AbstractConnection?,
    sender: String?
): SenderCredentials? {
    val senderName = sender?.takeIf { it.isNotBlank() } ?: return null
    val busConnection = connection as? DBusConnection
    val localSender = LocalJvmServiceRegistry.resolveLocalUniqueName(senderName) != null ||
        busConnection?.uniqueName == senderName
    val resolvedBusConnection = busConnection
        ?: return if (localSender) localProcessCredentialsOrNull() else null
    val dbus = runCatching {
        resolvedBusConnection.dynamicProxy(
            "org.freedesktop.DBus",
            "/org/freedesktop/DBus",
            DBus::class.java
        )
    }.getOrNull() ?: return if (localSender) localProcessCredentialsOrNull() else null

    val credentials = runCatching {
        dbus.GetConnectionCredentials(senderName)
    }.getOrNull().orEmpty()

    val pid = credentialUInt(
        firstCredentialValue(credentials, listOf("ProcessID", "UnixProcessID"))
    )?.toInt() ?: runCatching {
        dbus.GetConnectionUnixProcessID(senderName).toInt()
    }.getOrNull()

    val uid = credentialUInt(
        firstCredentialValue(credentials, listOf("UnixUserID", "UserID"))
    ) ?: runCatching {
        dbus.GetConnectionUnixUser(senderName).toLong().toUInt()
    }.getOrNull()

    val supplementaryGids = credentialUIntList(
        firstCredentialValue(credentials, listOf("UnixGroupIDs", "GroupIDs"))
    )
    val gid = credentialUInt(
        firstCredentialValue(credentials, listOf("UnixGroupID", "GroupID"))
    ) ?: supplementaryGids?.firstOrNull()

    val selinuxContext = credentialSelinuxContext(
        firstCredentialValue(credentials, listOf("LinuxSecurityLabel", "SELinuxContext"))
    ) ?: runCatching {
        credentialSelinuxContext(dbus.GetConnectionSELinuxSecurityContext(senderName))
    }.getOrNull()

    if (
        pid == null &&
        uid == null &&
        gid == null &&
        supplementaryGids == null &&
        selinuxContext == null
    ) {
        return if (localSender) localProcessCredentialsOrNull() else null
    }
    return SenderCredentials(
        pid = pid,
        uid = uid,
        gid = gid,
        supplementaryGids = supplementaryGids,
        selinuxContext = selinuxContext
    )
}

private fun Message.Metadata.withSenderCredentials(
    connection: AbstractConnection?,
    sender: String?
): Message.Metadata {
    val credentials = resolveSenderCredentials(connection, sender) ?: return this
    return copy(
        credsPid = credentials.pid,
        credsUid = credentials.uid,
        credsEuid = credentials.uid,
        credsGid = credentials.gid,
        credsEgid = credentials.gid,
        credsSupplementaryGids = credentials.supplementaryGids,
        selinuxContext = credentials.selinuxContext
    )
}

private fun parseMatchFilter(match: String): MatchFilter {
    val values = mutableMapOf<String, String>()
    match.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { token ->
            val parts = token.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim().trim('\'').trim('"')
                values[key] = value
            }
        }
    return MatchFilter(
        sender = values["sender"],
        path = values["path"],
        interfaceName = values["interface"],
        member = values["member"]
    )
}

private fun DBusSignal.matches(filter: MatchFilter): Boolean {
    val senderOk = filter.sender == null || source == filter.sender
    val pathOk = filter.path == null || path == filter.path
    val interfaceOk = filter.interfaceName == null || `interface` == filter.interfaceName
    val memberOk = filter.member == null || name == filter.member
    return senderOk && pathOk && interfaceOk && memberOk
}

private fun DBusSignal.toSdbusMessage(connection: AbstractConnection?): Signal = signalFromMetadata(
    Message.Metadata(
        interfaceName = `interface`,
        memberName = name,
        sender = source,
        path = path,
        destination = destination,
        valid = true,
        empty = false
    ).withSenderCredentials(connection, source)
).also { signal ->
    runCatching { parameters.toList() }.getOrElse { emptyList() }
        .map(::fromJavaSignalValue)
        .forEach { signal.payload.add(it) }
}

private fun fromJavaSignalValue(value: Any?): Any? = when (value) {
    is UInt16 -> value.toInt().toUShort()
    is UInt32 -> value.toLong().toUInt()
    is UInt64 -> value.value().toString().toULong()
    is org.freedesktop.dbus.DBusPath -> ObjectPath(value.path)
    is FileDescriptor -> UnixFd(value.intFileDescriptor)
    is org.freedesktop.dbus.types.Variant<*> -> fromJavaVariantValue(value)
    is List<*> -> value.map(::fromJavaSignalValue)
    is Array<*> -> value.map(::fromJavaSignalValue).toTypedArray()
    is Map<*, *> -> value.entries.associate { (k, v) ->
        fromJavaSignalValue(k) to fromJavaSignalValue(v)
    }

    else -> value
}

private fun toJavaSignalValue(value: Any?): Any? = when (value) {
    is UByte -> value.toByte()
    is UShort -> UInt16(value.toInt())
    is UInt -> UInt32(value.toLong())
    is ULong -> UInt64(value.toString())
    is BusName -> value.value
    is InterfaceName -> value.value
    is MemberName -> value.value
    is UnixFd -> FileDescriptor(value.fd)
    is com.monkopedia.sdbus.ObjectPath -> value.value
    is Signature -> value.value
    is Message.JvmVariantPayload -> toJavaVariantValue(value.signature, value.value)
    is Variant -> extractVariantPayload(value).let { payload ->
        toJavaVariantValue(payload.signature, payload.value)
    }
    is List<*> -> value.map(::toJavaSignalValue)
    is Array<*> -> value.map(::toJavaSignalValue).toTypedArray()
    is Map<*, *> -> value.entries.associate { (k, v) ->
        toJavaSignalValue(k) to toJavaSignalValue(v)
    }

    else -> value
}

private fun fromJavaVariantValue(value: org.freedesktop.dbus.types.Variant<*>): Variant {
    val signature = value.sig
        ?: inferSignalSignature(value.value)
        ?: throw createError(
            -1,
            "fromJavaVariantValue failed: unsupported payload type ${value.value?.javaClass}"
        )
    val message = PlainMessage.createPlainMessage()
    message.payload.add(
        Message.JvmVariantPayload(
            signature,
            fromJavaSignalValue(value.value)
        )
    )
    return Variant().apply { deserializeFrom(message) }
}

private fun extractVariantPayload(variant: Variant): Message.JvmVariantPayload {
    val signature = variant.peekValueType()
        ?: throw createError(-1, "Cannot convert empty variant to JVM D-Bus payload")
    val message = PlainMessage.createPlainMessage()
    variant.serializeTo(message)
    message.rewind(false)
    message.enterVariant(signature)
    val value = message.nextDeserializedValue("extractVariantPayload")
    message.exitVariant()
    return Message.JvmVariantPayload(signature, value)
}

private fun toJavaVariantValue(
    signature: String,
    value: Any?
): org.freedesktop.dbus.types.Variant<Any?> = org.freedesktop.dbus.types.Variant(
    toJavaSignalValue(value),
    signature
)

private fun inferSignalSignature(value: Any?): String? = when (value) {
    null -> null
    is Message.JvmVariantPayload -> "v"
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
    is BusName,
    is InterfaceName,
    is MemberName -> "s"
    is com.monkopedia.sdbus.ObjectPath -> "o"
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

private fun AbstractConnection.uniqueNameOrNull(): String? = (this as? DBusConnection)?.uniqueName

private class PureJavaDbusConnection(internal val javaConnection: AbstractConnection) :
    JvmDbusConnection {
    private var timeout: Duration = Duration.ZERO
    private val localUniqueName: String = javaConnection.uniqueNameOrNull().orEmpty()

    init {
        LocalJvmServiceRegistry.registerLocalUniqueName(localUniqueName)
    }

    override fun enterEventLoopAsync(): Unit = Unit

    override suspend fun leaveEventLoop(): Unit = Unit

    override fun currentlyProcessedMessage(): Message =
        JvmCurrentMessageContext.current() ?: com.monkopedia.sdbus.PlainMessage.createPlainMessage()

    override fun setMethodCallTimeout(timeout: Duration): Unit = run {
        this.timeout = timeout
    }

    override fun getMethodCallTimeout(): Duration = timeout

    override fun addObjectManager(objectPath: ObjectPath): Resource =
        LocalObjectManagerRegistry.register(objectPath.value, localUniqueName)

    override fun uniqueName(): BusName = BusName(localUniqueName.ifEmpty { ":jvm-direct" })

    override fun requestName(name: ServiceName): Unit = run {
        val busConnection = javaConnection as? DBusConnection ?: return
        runCatching {
            busConnection.requestBusName(name.value)
        }.getOrElse {
            throw createError(-1, "requestBusName failed: ${it.message}")
        }
        LocalJvmServiceRegistry.addAlias(localUniqueName, name.value)
    }

    override fun releaseName(name: ServiceName): Unit = run {
        val busConnection = javaConnection as? DBusConnection ?: return
        runCatching {
            busConnection.releaseBusName(name.value)
        }.getOrElse {
            throw createError(-1, "releaseBusName failed: ${it.message}")
        }
        LocalJvmServiceRegistry.removeAlias(localUniqueName, name.value)
    }

    override fun addMatch(match: String, callback: MessageHandler): Resource {
        val filter = parseMatchFilter(match)
        val matchRule = DBusMatchRule(
            "signal",
            filter.interfaceName,
            filter.member,
            filter.path
        )
        val closeable = runCatching {
            val handler = DBusSigHandler<DBusSignal> { signal ->
                if (signal.matches(filter)) {
                    val message = signal.toSdbusMessage(javaConnection)
                    JvmCurrentMessageContext.withMessage(message) {
                        callback(message)
                    }
                }
            }
            if (javaConnection is DBusConnection) {
                javaConnection.addGenericSigHandler(matchRule, handler)
            } else {
                addGenericSigHandler(javaConnection, matchRule, handler)
            }
        }.getOrElse {
            throw createError(-1, "addMatch failed: ${it.message}")
        }
        return CloseableResource(closeable)
    }

    override fun addMatchAsync(
        match: String,
        callback: MessageHandler,
        installCallback: MessageHandler
    ): Resource {
        val resource = addMatch(match, callback)
        installCallback(signalFromMetadata(Message.Metadata(valid = true, empty = true)))
        return resource
    }

    override fun release(): Unit = run {
        LocalJvmServiceRegistry.unregisterLocalUniqueName(localUniqueName)
        javaConnection.disconnect()
    }
}

private class PureJavaDbusProxy(
    private val connection: AbstractConnection?,
    private val destination: ServiceName,
    private val objectPath: ObjectPath,
    private val callerName: String?
) : JvmDbusProxy {
    private fun invalidReply(path: String, interfaceName: String, methodName: String): MethodReply =
        methodReplyFrom(
            Message.Metadata(
                interfaceName = interfaceName,
                memberName = methodName,
                sender = destination.value,
                path = path,
                destination = connection?.uniqueNameOrNull() ?: destination.value,
                valid = false,
                empty = true
            ),
            emptyList()
        )

    override fun currentlyProcessedMessage(): Message =
        JvmCurrentMessageContext.current() ?: signalFromMetadata(Message.Metadata())

    override fun createMethodCall(
        interfaceName: InterfaceName,
        methodName: MethodName
    ): MethodCall = com.monkopedia.sdbus.MethodCall().also {
        it.metadata = Message.Metadata(
            interfaceName = interfaceName.value,
            memberName = methodName.value,
            destination = destination.value,
            path = objectPath.value,
            valid = true,
            empty = true
        )
    }

    override fun callMethod(message: MethodCall): MethodReply {
        val interfaceName = message.getInterfaceName()
            ?: throw createError(-1, "callMethod failed: missing interface name")
        val methodName = message.getMemberName()
            ?: throw createError(-1, "callMethod failed: missing method name")
        val path = message.getPath() ?: objectPath.value
        val realConnection = connection
        if (realConnection != null) {
            val localDestination = resolveLocalDispatchDestination(realConnection)
            val dispatchDestination = localDestination ?: destination.value
            if (
                JvmStaticDispatch.hasHandler(
                    objectPath = path,
                    interfaceName = interfaceName,
                    methodName = methodName,
                    args = message.payload,
                    destination = dispatchDestination
                )
            ) {
                return callStaticDispatch(
                    message = message,
                    interfaceName = interfaceName,
                    methodName = methodName,
                    path = path,
                    dispatchDestination = dispatchDestination
                )
            }
            return callRemoteMethod(
                connection = realConnection,
                interfaceName = interfaceName,
                methodName = methodName,
                path = path,
                payload = message.payload.toList(),
                timeout = null
            )
        }
        return callStaticDispatch(
            message = message,
            interfaceName = interfaceName,
            methodName = methodName,
            path = path,
            dispatchDestination = destination.value
        )
    }

    private fun callStaticDispatch(
        message: MethodCall,
        interfaceName: String,
        methodName: String,
        path: String,
        dispatchDestination: String
    ): MethodReply {
        val inboundSender = callerName ?: connection?.uniqueNameOrNull()
        val inbound = MethodCall().also {
            it.metadata = Message.Metadata(
                interfaceName = interfaceName,
                memberName = methodName,
                sender = inboundSender,
                destination = destination.value,
                path = path,
                valid = true,
                empty = message.payload.isEmpty()
            ).withSenderCredentials(connection, inboundSender)
            it.payload.addAll(message.payload)
        }
        val result = runCatching {
            JvmCurrentMessageContext.withMessage(inbound) {
                JvmStaticDispatch.invokeOrNull(
                    objectPath = path,
                    interfaceName = interfaceName,
                    methodName = methodName,
                    args = message.payload,
                    destination = dispatchDestination
                )
            }
        }.getOrElse {
            throw createError(-1, "callMethod failed: ${it.message}")
        } ?: throw createError(
            -1,
            "callMethod failed: no static binding for $path:$interfaceName.$methodName/${message.payload.size}"
        )
        val values = when (result) {
            Unit -> emptyList()
            is JvmStaticDispatch.DispatchResult -> {
                result.reply.error?.let { throw it }
                if (!result.reply.isValid) {
                    throw createError(-1, "callMethod failed: method returned an invalid reply")
                }
                result.reply.payload.toList()
            }
            else -> listOf(result)
        }
        return methodReplyFrom(
            Message.Metadata(
                interfaceName = interfaceName,
                memberName = methodName,
                sender = destination.value,
                path = path,
                destination = connection?.uniqueNameOrNull() ?: destination.value,
                valid = true,
                empty = values.isEmpty()
            ),
            values
        )
    }

    override fun callMethod(message: MethodCall, timeout: ULong): MethodReply {
        val interfaceName = message.getInterfaceName()
            ?: throw createError(-1, "callMethod failed: missing interface name")
        val methodName = message.getMemberName()
            ?: throw createError(-1, "callMethod failed: missing method name")
        val path = message.getPath() ?: objectPath.value
        val realConnection = connection
        if (realConnection != null) {
            val localDestination = resolveLocalDispatchDestination(realConnection)
            val dispatchDestination = localDestination ?: destination.value
            if (
                JvmStaticDispatch.hasHandler(
                    objectPath = path,
                    interfaceName = interfaceName,
                    methodName = methodName,
                    args = message.payload,
                    destination = dispatchDestination
                )
            ) {
                return if (timeout == 0uL) {
                    callStaticDispatch(
                        message = message,
                        interfaceName = interfaceName,
                        methodName = methodName,
                        path = path,
                        dispatchDestination = dispatchDestination
                    )
                } else {
                    callWithTimeout(
                        message = message,
                        interfaceName = interfaceName,
                        methodName = methodName,
                        path = path,
                        timeout = timeout,
                        dispatchDestination = dispatchDestination
                    )
                }
            }
            return callRemoteMethod(
                connection = realConnection,
                interfaceName = interfaceName,
                methodName = methodName,
                path = path,
                payload = message.payload.toList(),
                timeout = timeout.takeUnless { it == 0uL }
            )
        }
        if (timeout == 0uL) {
            return callStaticDispatch(
                message = message,
                interfaceName = interfaceName,
                methodName = methodName,
                path = path,
                dispatchDestination = destination.value
            )
        }
        return callWithTimeout(
            message = message,
            interfaceName = interfaceName,
            methodName = methodName,
            path = path,
            timeout = timeout,
            dispatchDestination = destination.value
        )
    }

    private fun callWithTimeout(
        message: MethodCall,
        interfaceName: String,
        methodName: String,
        path: String,
        timeout: ULong,
        dispatchDestination: String
    ): MethodReply {
        val result = AtomicReference<MethodReply?>()
        val failure = AtomicReference<Throwable?>()
        val done = java.util.concurrent.CountDownLatch(1)
        thread(start = true, isDaemon = true, name = "sdbus-jvm-sync-timeout") {
            runCatching {
                callStaticDispatch(
                    message = message,
                    interfaceName = interfaceName,
                    methodName = methodName,
                    path = path,
                    dispatchDestination = dispatchDestination
                )
            }
                .onSuccess { result.set(it) }
                .onFailure { failure.set(it) }
            done.countDown()
        }
        val timeoutMillis = ((timeout + 999uL) / 1000uL).toLong().coerceAtLeast(1L)
        if (!done.await(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            throw createError(-1, "Method call timed out")
        }
        failure.get()?.let { throw it }
        return result.get() ?: throw createError(-1, "callMethod failed: missing result")
    }

    private fun resolveLocalDispatchDestination(realConnection: AbstractConnection): String? {
        val destinationName = destination.value
        val localUniqueName = realConnection.uniqueNameOrNull().orEmpty()
        if (localUniqueName.isEmpty()) return null
        if (destinationName == localUniqueName) return localUniqueName

        val localRegistryOwner = LocalJvmServiceRegistry.resolveLocalUniqueName(destinationName)
        if (localRegistryOwner == localUniqueName) return localUniqueName

        val localBusOwner = (realConnection as? DBusConnection)?.let { connection ->
            if (destinationName.isEmpty()) return@let null
            runCatching {
                connection.getDBusOwnerName(destinationName)
            }.getOrNull()
        }
        return localUniqueName.takeIf { it == localBusOwner }
    }

    private fun callRemoteMethod(
        connection: AbstractConnection,
        interfaceName: String,
        methodName: String,
        path: String,
        payload: List<Any?>,
        timeout: ULong?
    ): MethodReply {
        val signature = if (payload.isEmpty()) {
            null
        } else {
            payload.joinToString(separator = "") { value ->
                inferSignalSignature(value)
                    ?: throw createError(
                        -1,
                        "callMethod failed: unsupported argument type ${
                            value?.let { typed -> typed::class.simpleName } ?: "null"
                        }"
                    )
            }
        }
        val args = payload.map(::toJavaSignalValue).toTypedArray()
        val destinationName = destination.value.ifEmpty { null }
        val rawCall = runCatching {
            connection.messageFactory.createMethodCall(
                null,
                destinationName,
                path,
                interfaceName,
                methodName,
                0,
                signature,
                *args
            )
        }.getOrElse {
            throw createError(-1, "callMethod failed: ${it.message}")
        }
        runCatching {
            connection.sendMessage(rawCall)
        }.getOrElse {
            throw createError(-1, "callMethod failed: ${it.message}")
        }
        val rawReply = runCatching {
            if (timeout == null) {
                rawCall.reply
            } else {
                rawCall.getReply(((timeout + 999uL) / 1000uL).toLong().coerceAtLeast(1L))
            }
        }.getOrElse {
            throw createError(-1, "callMethod failed: ${it.message}")
        } ?: throw createError(-1, "Method call timed out")

        if (rawReply is org.freedesktop.dbus.messages.Error) {
            val remoteError = runCatching {
                rawReply.throwException()
                null
            }.exceptionOrNull()
            throw createError(-1, remoteError?.message ?: "Remote method call failed")
        }
        val values = runCatching {
            rawReply.parameters.toList()
        }.getOrElse {
            throw createError(-1, "callMethod failed: ${it.message}")
        }.map(::fromJavaSignalValue)
        val sender = rawReply.source ?: destination.value
        return methodReplyFrom(
            Message.Metadata(
                interfaceName = interfaceName,
                memberName = methodName,
                sender = sender,
                path = rawReply.path ?: path,
                destination = rawReply.destination,
                valid = true,
                empty = values.isEmpty()
            ).withSenderCredentials(connection, sender),
            values
        )
    }

    override fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: com.monkopedia.sdbus.AsyncReplyHandler
    ): PendingAsyncCall = callMethodAsync(message, asyncReplyCallback, 0u)

    override fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: com.monkopedia.sdbus.AsyncReplyHandler,
        timeout: ULong
    ): PendingAsyncCall {
        val cancelled = AtomicBoolean(false)
        val pending = AtomicBoolean(true)
        val interfaceName = message.getInterfaceName().orEmpty()
        val methodName = message.getMemberName().orEmpty()
        val path = message.getPath() ?: objectPath.value

        thread(
            start = true,
            isDaemon = true,
            name = "sdbus-jvm-call-$interfaceName.$methodName"
        ) {
            val outcome = runCatching {
                if (timeout == 0uL) callMethod(message) else callMethod(message, timeout)
            }
            pending.set(false)
            if (cancelled.get()) return@thread
            outcome.fold(
                onSuccess = { asyncReplyCallback(it, null) },
                onFailure = {
                    val error = it as? com.monkopedia.sdbus.Error
                        ?: createError(-1, it.message ?: "JVM async call failed")
                    asyncReplyCallback(invalidReply(path, interfaceName, methodName), error)
                }
            )
        }

        return PendingAsyncCall(
            cancelAction = { cancelled.set(true) },
            isPendingAction = { pending.get() }
        )
    }

    override suspend fun callMethodAsync(message: MethodCall): MethodReply = callMethod(message)

    override suspend fun callMethodAsync(message: MethodCall, timeout: ULong): MethodReply =
        if (timeout == 0uL) callMethod(message) else callMethod(message, timeout)

    override fun registerSignalHandler(
        interfaceName: InterfaceName,
        signalName: SignalName,
        signalHandler: SignalHandler
    ): Resource {
        val realConnection = connection
        if (realConnection == null) {
            return LocalJvmSignalBus.register(
                destination = destination.value,
                path = objectPath.value,
                interfaceName = interfaceName.value,
                member = signalName.value,
                signalHandler = signalHandler
            )
        }
        val resolvedDestination = (realConnection as? DBusConnection)?.let { connection ->
            runCatching {
                connection.getDBusOwnerName(destination.value)
            }.getOrNull()
        }
        val matchRule = DBusMatchRule(
            "signal",
            interfaceName.value,
            signalName.value,
            objectPath.value
        )
        val closeable = runCatching {
            val callback = DBusSigHandler<DBusSignal> { signal ->
                val matchesDestination = destination.value.isEmpty() ||
                    signal.source == destination.value ||
                    (
                        resolvedDestination?.isNotEmpty() == true &&
                            signal.source == resolvedDestination
                        )
                val matchesPath = signal.path == objectPath.value
                val matchesInterface = signal.`interface` == interfaceName.value
                val matchesMember = signal.name == signalName.value
                if (matchesDestination && matchesPath && matchesInterface && matchesMember) {
                    val message = signal.toSdbusMessage(realConnection)
                    JvmCurrentMessageContext.withMessage(message) {
                        signalHandler(message)
                    }
                }
            }
            if (realConnection is DBusConnection) {
                realConnection.addGenericSigHandler(matchRule, callback)
            } else {
                addGenericSigHandler(realConnection, matchRule, callback)
            }
        }.getOrElse {
            throw createError(-1, "registerSignalHandler failed: ${it.message}")
        }
        return CloseableResource(closeable)
    }

    override fun release(): Unit = Unit
}

private fun addGenericSigHandler(
    connection: AbstractConnection,
    matchRule: DBusMatchRule,
    callback: DBusSigHandler<DBusSignal>
): AutoCloseable {
    val method = AbstractConnection::class.java.getDeclaredMethod(
        "addGenericSigHandler",
        DBusMatchRule::class.java,
        DBusSigHandler::class.java
    ).apply { isAccessible = true }
    return method.invoke(connection, matchRule, callback) as AutoCloseable
}

private class PureJavaDbusObject(
    private val objectPath: ObjectPath,
    private val javaConnection: AbstractConnection?,
    private val senderName: String?
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
    private val dispatchDestination = senderName ?: javaConnection?.uniqueNameOrNull().orEmpty()

    private fun countTopLevelTypes(signature: String?): Int {
        if (signature.isNullOrEmpty()) return 0
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

        var index = 0
        var count = 0
        while (index < signature.length) {
            val next = parseOne(index)
            if (next <= index) break
            count++
            index = next
        }
        return count
    }

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
                sender = inbound?.getSender(),
                destination = inbound?.getDestination(),
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

    override fun emitPropertiesChangedSignal(
        interfaceName: InterfaceName,
        propNames: List<PropertyName>
    ) {
        val sender = senderName ?: javaConnection?.uniqueNameOrNull()
        val changedProperties = emptyMap<PropertyName, Variant>()
        val invalidatedProperties = propNames
        val payload = listOf(interfaceName, changedProperties, invalidatedProperties)
        val realConnection = javaConnection
        if (realConnection == null) {
            LocalJvmSignalBus.emit(
                sender = sender,
                path = objectPath.value,
                interfaceName = propertiesInterfaceName,
                member = propertiesChangedMemberName,
                payload = payload
            )
            return
        }
        val rawSignal = runCatching {
            realConnection.messageFactory.createSignal(
                sender ?: realConnection.uniqueNameOrNull(),
                objectPath.value,
                propertiesInterfaceName,
                propertiesChangedMemberName,
                propertiesChangedSignature,
                interfaceName.value,
                emptyMap<String, org.freedesktop.dbus.types.Variant<Any?>>(),
                invalidatedProperties.map { it.value }
            )
        }.getOrElse {
            throw createError(-1, "emitPropertiesChangedSignal failed: ${it.message}")
        }
        runCatching { realConnection.sendMessage(rawSignal) }.getOrElse {
            throw createError(-1, "emitPropertiesChangedSignal failed: ${it.message}")
        }
    }

    override fun emitPropertiesChangedSignal(interfaceName: InterfaceName) {
        emitPropertiesChangedSignal(interfaceName, emptyList())
    }

    override fun emitInterfacesAddedSignal() {
        emitInterfacesAddedSignal(registeredInterfaces.keys.map(::InterfaceName))
    }

    override fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>) {
        val sender = senderName ?: javaConnection?.uniqueNameOrNull()
        val signalPath = LocalObjectManagerRegistry.resolveFor(objectPath.value) ?: objectPath.value
        val payload = listOf(
            objectPath,
            interfaces.associateWith { emptyMap<PropertyName, Variant>() }
        )
        val realConnection = javaConnection
        if (realConnection == null) {
            LocalJvmSignalBus.emit(
                sender = sender,
                path = signalPath,
                interfaceName = objectManagerInterfaceName,
                member = interfacesAddedMemberName,
                payload = payload
            )
            return
        }
        val javaMap: Map<String, Map<String, org.freedesktop.dbus.types.Variant<Any?>>> =
            interfaces.associate { it.value to emptyMap() }
        val rawSignal = runCatching {
            realConnection.messageFactory.createSignal(
                sender ?: realConnection.uniqueNameOrNull(),
                signalPath,
                objectManagerInterfaceName,
                interfacesAddedMemberName,
                interfacesAddedSignature,
                objectPath.value,
                javaMap
            )
        }.getOrElse {
            throw createError(-1, "emitInterfacesAddedSignal failed: ${it.message}")
        }
        runCatching { realConnection.sendMessage(rawSignal) }.getOrElse {
            throw createError(-1, "emitInterfacesAddedSignal failed: ${it.message}")
        }
    }

    override fun emitInterfacesRemovedSignal() {
        emitInterfacesRemovedSignal(registeredInterfaces.keys.map(::InterfaceName))
    }

    override fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>) {
        val sender = senderName ?: javaConnection?.uniqueNameOrNull()
        val signalPath = LocalObjectManagerRegistry.resolveFor(objectPath.value) ?: objectPath.value
        val payload = listOf(objectPath, interfaces)
        val realConnection = javaConnection
        if (realConnection == null) {
            LocalJvmSignalBus.emit(
                sender = sender,
                path = signalPath,
                interfaceName = objectManagerInterfaceName,
                member = interfacesRemovedMemberName,
                payload = payload
            )
            return
        }
        val rawSignal = runCatching {
            realConnection.messageFactory.createSignal(
                sender ?: realConnection.uniqueNameOrNull(),
                signalPath,
                objectManagerInterfaceName,
                interfacesRemovedMemberName,
                interfacesRemovedSignature,
                objectPath.value,
                interfaces.map { it.value }
            )
        }.getOrElse {
            throw createError(-1, "emitInterfacesRemovedSignal failed: ${it.message}")
        }
        runCatching { realConnection.sendMessage(rawSignal) }.getOrElse {
            throw createError(-1, "emitInterfacesRemovedSignal failed: ${it.message}")
        }
    }

    override fun addObjectManager(): Resource =
        LocalObjectManagerRegistry.register(objectPath.value, dispatchDestination).also {
            registrations += it
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
                        sender = inbound?.getSender(),
                        destination = inbound?.getDestination(),
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
            }
        }
    }

    override fun createSignal(interfaceName: InterfaceName, signalName: SignalName): Signal =
        signalFromMetadata(
            Message.Metadata(
                interfaceName = interfaceName.value,
                memberName = signalName.value,
                sender = senderName ?: javaConnection?.uniqueNameOrNull(),
                path = objectPath.value,
                valid = true,
                empty = true
            )
        ) { emitSignal(it) }

    override fun emitSignal(message: Signal) {
        val interfaceName = message.getInterfaceName()
            ?: throw createError(-1, "emitSignal failed: missing interface name")
        val signalName = message.getMemberName()
            ?: throw createError(-1, "emitSignal failed: missing signal name")
        val path = message.getPath() ?: objectPath.value
        val signature = message.payload.joinToString("") { value ->
            inferSignalSignature(value)
                ?: throw createError(-1, "emitSignal failed: unsupported signal payload type")
        }
        val args = message.payload.map(::toJavaSignalValue).toTypedArray()
        val sender = message.getSender() ?: senderName ?: javaConnection?.uniqueNameOrNull()
        val realConnection = javaConnection
        if (realConnection == null) {
            LocalJvmSignalBus.emit(sender, path, interfaceName, signalName, message.payload)
            return
        }
        val rawSignal = runCatching {
            realConnection.messageFactory.createSignal(
                sender ?: realConnection.uniqueNameOrNull(),
                path,
                interfaceName,
                signalName,
                signature.ifEmpty { null },
                *args
            )
        }.getOrElse {
            throw createError(-1, "emitSignal failed: ${it.message}")
        }
        runCatching { realConnection.sendMessage(rawSignal) }.getOrElse {
            throw createError(-1, "emitSignal failed: ${it.message}")
        }
    }

    override fun release(): Unit = run {
        registrations.forEach { it.release() }
        registrations.clear()
    }
}
