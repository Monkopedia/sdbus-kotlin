package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.AsyncReplyHandler
import com.monkopedia.sdbus.BusName
import com.monkopedia.sdbus.Connection
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.JvmConnection
import com.monkopedia.sdbus.MemberName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MessageHandler
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MethodReply
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PendingAsyncCall
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalHandler
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.createError
import com.monkopedia.sdbus.createPlainMessage
import com.monkopedia.sdbus.methodReplyFrom
import com.monkopedia.sdbus.signalFromMetadata
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.time.Duration

/**
 * The JVM D-Bus backend (epic #93): the one and only backend, routing everything through the
 * self-owned D-Bus connection ([DBusWireConnection]) — raw junixsocket transport, our own
 * marshaller and read/dispatch loop. dbus-java has been retired (phase 6).
 *
 * Scope, with a consistent same-process/cross-process split:
 * - method calls (sync + async) to a REMOTE peer and Properties Get/Set/GetAll go over the wire;
 *   a call to an object served by one of our OWN connections is dispatched in-process through the
 *   shared static-dispatch tables ([JvmStaticDispatch], [LocalObjectManagerRegistry], ...), exactly
 *   as the dbus-java backend short-circuits same-process calls;
 * - signal EMISSION goes OVER THE WIRE (phase 3b): an object served on the owned connection emits a
 *   real D-Bus SIGNAL via [emitWireSignal], so the bus routes it to every subscriber -- external
 *   processes AND same-JVM connections alike;
 * - signal RECEPTION is correspondingly wire-only: every subscriber (whether listening to an
 *   external peer or to one of our own objects) registers `AddMatch` + a reader filter via
 *   [installSignalMatch] over its own socket, so each signal is delivered exactly once. The
 *   in-process [LocalJvmSignalBus]/[LocalJvmMatchBus] is NOT used on this backend -- it stays the
 *   dbus-java backend's same-process mechanism. Sender credentials of a signal from one of our own
 *   connections are filled from the local process ([withLocalSenderCredentials]);
 * - what stays DEFERRED is incoming method-call SERVING over the wire (phase 4).
 */
internal class WireDbusBackend : JvmDbusBackend {
    override fun createConnection(
        busType: JvmBusType,
        endpoint: String?,
        name: ServiceName?,
        fd: Int?
    ): JvmDbusConnection {
        if (fd != null || busType == JvmBusType.DIRECT_FD || busType == JvmBusType.SERVER_FD) {
            throw createError(-1, "JVM wire backend does not support fd-based connections")
        }
        val wire = runCatching {
            when (busType) {
                JvmBusType.DEFAULT, JvmBusType.SESSION -> DBusWireConnection.connectSession()
                JvmBusType.SYSTEM -> DBusWireConnection.connectSystem()
                JvmBusType.SESSION_ADDRESS -> endpoint?.let(DBusWireConnection::connect)
                // A DIRECT (brokerless) endpoint is peer-to-peer with no message-bus daemon: open
                // it in direct mode so the connection skips the org.freedesktop.DBus bootstrap
                // (Hello/RequestName/AddMatch) that an UnknownObject would otherwise fail on.
                JvmBusType.DIRECT_ADDRESS -> endpoint?.let(DBusWireConnection::connectDirect)
                JvmBusType.DIRECT_FD, JvmBusType.SERVER_FD -> null
            }
        }.getOrElse {
            throw createError(
                -1,
                "Failed to open bus${endpoint?.let { e -> " '$e'" }.orEmpty()}: ${it.message}"
            )
        }
            ?: throw createError(
                -1,
                "Failed to open bus: unsupported connection request for $busType"
            )
        return WireDbusConnection(wire).also { connection ->
            if (name != null) connection.requestName(name)
        }
    }

    override fun createProxy(
        connection: Connection,
        destination: ServiceName,
        objectPath: ObjectPath,
        runEventLoopThread: Boolean
    ): JvmDbusProxy {
        if (runEventLoopThread) connection.startEventLoop()
        val wireConnection = (connection as? JvmConnection)?.backend as? WireDbusConnection
        return WireDbusProxy(wireConnection, destination, objectPath)
    }

    override fun createObject(connection: Connection, objectPath: ObjectPath): JvmDbusObject {
        // Method dispatch and managed-object/property bookkeeping live in the shared in-process
        // registries; signal EMISSION goes OVER THE WIRE (epic #93 phase 3b): we hand WireDbusObject
        // a WireSignalEmitter bound to this connection's socket, so every signal it emits is a real
        // D-Bus SIGNAL the bus routes to all subscribers (external processes AND same-JVM
        // connections, each via its own AddMatch) -- uniform with the native backend, and the only
        // way a receiver can resolve our sender credentials off the bus.
        val wire = ((connection as? JvmConnection)?.backend as? WireDbusConnection)?.wireConnection
            ?: throw createError(-1, "createObject failed: connection has no wire transport")
        val emitter = WireSignalEmitter { path, interfaceName, member, signature, payload ->
            emitWireSignal(wire, path, interfaceName, member, signature, payload)
        }
        return WireDbusObject(
            objectPath,
            runCatching { connection.uniqueName.value }.getOrNull(),
            emitter
        )
    }
}

/**
 * Marshals [payload] (converted from the high-level value model via [toWireBodyValue]) against
 * [signature] and sends it as a broadcast D-Bus SIGNAL on [wire]. The SENDER header is left unset:
 * the bus daemon stamps the authoritative sender (our unique name) and attaches our credentials.
 * An empty/blank signature means a no-argument signal (no body).
 */
private fun emitWireSignal(
    wire: DBusWireConnection,
    path: String,
    interfaceName: String,
    member: String,
    signature: String?,
    payload: List<Any?>
) {
    val effectiveSignature = signature?.takeUnless { it.isEmpty() }
    val body = if (effectiveSignature == null) emptyList() else payload.map(::toWireBodyValue)
    runCatching {
        wire.send(
            WireMessage(
                type = WireMessageType.SIGNAL,
                path = path,
                interfaceName = interfaceName,
                member = member,
                signature = effectiveSignature,
                body = body
            )
        )
    }.getOrElse {
        throw createError(-1, "emitSignal failed: ${it.message}")
    }
}

internal class WireDbusConnection(private val wire: DBusWireConnection) : JvmDbusConnection {
    private val localUniqueName: String = wire.uniqueName.orEmpty()
    private val released = AtomicBoolean(false)
    private var timeout: Duration = Duration.ZERO

    val wireConnection: DBusWireConnection get() = wire
    fun isReleased(): Boolean = released.get()
    fun defaultTimeoutMicros(): ULong = timeout.inWholeMicroseconds.coerceAtLeast(0L).toULong()

    init {
        LocalJvmServiceRegistry.registerLocalUniqueName(localUniqueName)
        // Serve incoming method calls (epic #93 phase 4 — closes #90): route every METHOD_CALL the
        // bus delivers to one of our exported objects through the shared dispatch tables and send
        // the reply. Runs on the connection's serve worker, never the reader thread.
        wire.setIncomingCallHandler { message ->
            WireServe.handleIncomingCall(wire, localUniqueName, message)
        }
    }

    override fun startEventLoop(): Unit = Unit

    override suspend fun stopEventLoop(): Unit = Unit

    override fun currentlyProcessedMessage(): Message =
        JvmCurrentMessageContext.current() ?: createPlainMessage()

    override fun setMethodCallTimeout(timeout: Duration): Unit = run { this.timeout = timeout }

    override fun getMethodCallTimeout(): Duration = timeout

    override fun addObjectManager(objectPath: ObjectPath): Resource =
        LocalObjectManagerRegistry.register(objectPath.value, localUniqueName)

    override fun uniqueName(): BusName = BusName(localUniqueName.ifEmpty { ":jvm-wire" })

    override fun requestName(name: ServiceName) {
        val result = runCatching { wire.requestName(name.value) }.getOrElse {
            throw createError(-1, "requestName failed: ${it.message}")
        }
        // 1 = primary owner, 4 = already owner. Anything else means we did not get the name.
        if (result != 1 && result != 4) {
            throw createError(
                -1,
                "requestName failed: RequestName returned $result for ${name.value}"
            )
        }
        LocalJvmServiceRegistry.addAlias(localUniqueName, name.value)
    }

    override fun releaseName(name: ServiceName) {
        runCatching { wire.releaseName(name.value) }.getOrElse {
            throw createError(-1, "releaseName failed: ${it.message}")
        }
        LocalJvmServiceRegistry.removeAlias(localUniqueName, name.value)
    }

    override fun addMatch(match: String, callback: MessageHandler): Resource =
        installSignalMatch(wire, match, parseMatchSpec(match), callback)

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        LocalJvmServiceRegistry.unregisterLocalUniqueName(localUniqueName)
        runCatching { wire.close() }
    }
}

internal class WireDbusProxy(
    private val connection: WireDbusConnection?,
    private val destination: ServiceName,
    private val objectPath: ObjectPath
) : JvmDbusProxy {
    private val wire: DBusWireConnection? get() = connection?.wireConnection

    override fun currentlyProcessedMessage(): Message =
        JvmCurrentMessageContext.current() ?: signalFromMetadata(Message.Metadata())

    override fun createMethodCall(
        interfaceName: InterfaceName,
        methodName: MethodName
    ): MethodCall = MethodCall().also {
        it.metadata = Message.Metadata(
            interfaceName = interfaceName.value,
            memberName = methodName.value,
            destination = destination.value,
            path = objectPath.value,
            valid = true,
            empty = true
        )
    }

    override fun callMethod(message: MethodCall): MethodReply = callMethod(message, 0uL)

    override fun callMethod(message: MethodCall, timeout: ULong): MethodReply {
        // Native parity (issue #81): a call after the proxy's connection was released must throw,
        // never be serviced (e.g. via the in-process dispatch short-circuit). ConnectionImpl guards
        // every post-release op with require(!released) { "Connection has already been released" }.
        require(connection?.isReleased() != true) { "Connection has already been released" }
        val effectiveTimeout =
            if (timeout == 0uL) connection?.defaultTimeoutMicros() ?: 0uL else timeout
        val interfaceName = message.interfaceName?.value
            ?: throw createError(-1, "callMethod failed: missing interface name")
        val methodName = message.memberName?.value
            ?: throw createError(-1, "callMethod failed: missing method name")
        val path = message.objectPath?.value ?: objectPath.value

        // Same-process serving: a call to an object served by any connection in our own process is
        // dispatched in-process through the shared static-dispatch table (this is not wire serving;
        // it is the same local dispatch the dbus-java backend uses for same-process calls). The
        // dispatch key prefers the owning local unique name but falls back to the destination, so
        // wildcard ("") and single-candidate registrations resolve exactly as dbus-java's do.
        val localOwner = localDispatchOwnerOrNull()
        val dispatchDestination = localOwner ?: destination.value
        if (JvmStaticDispatch.hasHandler(
                path,
                interfaceName,
                methodName,
                message.payload,
                dispatchDestination
            )
        ) {
            return if (effectiveTimeout == 0uL) {
                callLocalDispatch(message, interfaceName, methodName, path, dispatchDestination)
            } else {
                callLocalDispatchWithTimeout(
                    message,
                    interfaceName,
                    methodName,
                    path,
                    dispatchDestination,
                    effectiveTimeout
                )
            }
        }
        if (localOwner != null) {
            // The destination is one of our own connections but nothing serves this member.
            // We must NOT put the call on the wire: phase 3 does not serve incoming wire calls
            // (phase 4), so our own peer would never reply and the caller would hang until
            // timeout. Fail fast with UnknownMethod, the same outcome dbus-java produces by
            // auto-replying for an unexported member.
            throw com.monkopedia.sdbus.SdbusException(
                "org.freedesktop.DBus.Error.UnknownMethod",
                "No handler for $path:$interfaceName.$methodName on local destination " +
                    destination.value
            )
        }

        val realWire = wire
            ?: throw createError(-1, "callMethod failed: connection has no wire transport")
        return callRemote(realWire, interfaceName, methodName, path, message, effectiveTimeout)
    }

    private fun localDispatchOwnerOrNull(): String? {
        val localUnique = connection?.uniqueName()?.value ?: return null
        val destName = destination.value
        if (destName == localUnique) return localUnique
        return LocalJvmServiceRegistry.resolveLocalUniqueName(destName)
    }

    // Honors a per-call timeout for in-process dispatch (which may block on an async vtable
    // handler): runs the dispatch on a daemon thread and fails with a timeout error if it does not
    // complete in time, mirroring the dbus-java backend's callWithTimeout.
    private fun callLocalDispatchWithTimeout(
        message: MethodCall,
        interfaceName: String,
        methodName: String,
        path: String,
        dispatchDestination: String,
        timeoutMicros: ULong
    ): MethodReply {
        val result = java.util.concurrent.atomic.AtomicReference<MethodReply?>()
        val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val done = java.util.concurrent.CountDownLatch(1)
        thread(start = true, isDaemon = true, name = "sdbus-jvm-wire-local-timeout") {
            runCatching {
                callLocalDispatch(message, interfaceName, methodName, path, dispatchDestination)
            }.onSuccess { result.set(it) }.onFailure { failure.set(it) }
            done.countDown()
        }
        if (!done.await(
                microsToMillis(timeoutMicros),
                java.util.concurrent.TimeUnit.MILLISECONDS
            )
        ) {
            throw com.monkopedia.sdbus.SdbusException(
                "org.freedesktop.DBus.Error.Timeout",
                "Method call timed out"
            )
        }
        failure.get()?.let { throw it }
        return result.get() ?: throw createError(-1, "callMethod failed: missing result")
    }

    private fun callLocalDispatch(
        message: MethodCall,
        interfaceName: String,
        methodName: String,
        path: String,
        dispatchDestination: String
    ): MethodReply {
        val inboundSender = connection?.uniqueName()?.value
        val inbound = MethodCall().also {
            it.metadata = Message.Metadata(
                interfaceName = interfaceName,
                memberName = methodName,
                sender = inboundSender,
                destination = destination.value,
                path = path,
                valid = true,
                empty = message.payload.isEmpty()
            )
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
            "callMethod failed: no static binding for $path:$interfaceName.$methodName"
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
                destination = connection?.uniqueName()?.value ?: destination.value,
                valid = true,
                empty = values.isEmpty()
            ),
            values
        )
    }

    private fun callRemote(
        realWire: DBusWireConnection,
        interfaceName: String,
        methodName: String,
        path: String,
        message: MethodCall,
        timeoutMicros: ULong
    ): MethodReply {
        val payload = message.payload.toList()
        val signature = wireBodySignature(payload, message.declaredBodySignature.toString())
        val request = WireMessage(
            type = WireMessageType.METHOD_CALL,
            destination = destination.value.ifEmpty { null },
            path = path,
            interfaceName = interfaceName,
            member = methodName,
            signature = signature,
            body = if (signature == null) emptyList() else payload.map(::toWireBodyValue)
        )
        val reply = try {
            if (timeoutMicros == 0uL) {
                realWire.callBlocking(request)
            } else {
                realWire.callBlocking(request, microsToMillis(timeoutMicros))
            }
        } catch (e: DBusCallException) {
            // Preserve the wire ERROR_NAME verbatim, like the native backend copies it straight
            // into sd_bus_error instead of squeezing it through the errno mapping (issue #72).
            throw com.monkopedia.sdbus.SdbusException(
                e.errorName ?: "org.freedesktop.DBus.Error.Failed",
                e.errorMessage.orEmpty()
            )
        } catch (e: IOException) {
            throw createError(-1, "callMethod failed: ${e.message}")
        }
        return methodReplyFrom(
            Message.Metadata(
                interfaceName = interfaceName,
                memberName = methodName,
                sender = reply.sender ?: destination.value,
                path = reply.path ?: path,
                destination = reply.destination,
                valid = true,
                empty = reply.body.isEmpty()
            ),
            fromWireReplyValues(reply.body, reply.signature)
        )
    }

    override fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler
    ): PendingAsyncCall = callMethodAsync(message, asyncReplyCallback, 0u)

    override fun callMethodAsync(
        message: MethodCall,
        asyncReplyCallback: AsyncReplyHandler,
        timeout: ULong
    ): PendingAsyncCall {
        val cancelled = AtomicBoolean(false)
        val pending = AtomicBoolean(true)
        val interfaceName = message.interfaceName?.value.orEmpty()
        val methodName = message.memberName?.value.orEmpty()
        val path = message.objectPath?.value ?: objectPath.value
        thread(
            start = true,
            isDaemon = true,
            name = "sdbus-jvm-wire-call-$interfaceName.$methodName"
        ) {
            val outcome = runCatching {
                if (timeout == 0uL) callMethod(message) else callMethod(message, timeout)
            }
            pending.set(false)
            if (cancelled.get()) return@thread
            outcome.fold(
                onSuccess = { asyncReplyCallback(it, null) },
                onFailure = {
                    val error = it as? com.monkopedia.sdbus.SdbusException
                        ?: createError(-1, it.message ?: "JVM wire async call failed")
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

    private fun invalidReply(path: String, interfaceName: String, methodName: String): MethodReply =
        methodReplyFrom(
            Message.Metadata(
                interfaceName = interfaceName,
                memberName = methodName,
                sender = destination.value,
                path = path,
                valid = false,
                empty = true
            ),
            emptyList()
        )

    override fun registerSignalHandler(
        interfaceName: InterfaceName,
        signalName: SignalName,
        signalHandler: SignalHandler
    ): Resource {
        val realWire = wire ?: throw createError(
            -1,
            "registerSignalHandler failed: connection has no wire transport"
        )
        val owner = destination.value
            .takeIf { it.isNotEmpty() && !it.startsWith(":") }
            ?.let { realWire.getNameOwner(it) }
        val spec = MatchSpec(
            sender = destination.value.ifEmpty { null },
            path = objectPath.value,
            interfaceName = interfaceName.value,
            member = signalName.value,
            resolvedOwner = owner
        )
        val rule = buildMatchRule(spec)
        return installSignalMatch(realWire, rule, spec) { message ->
            signalHandler(message as com.monkopedia.sdbus.Signal)
        }
    }

    override fun release(): Unit = Unit
}

/** A parsed signal match used to filter incoming SIGNAL messages on the connection. */
private data class MatchSpec(
    val sender: String? = null,
    val path: String? = null,
    val interfaceName: String? = null,
    val member: String? = null,
    val resolvedOwner: String? = null
)

private fun parseMatchSpec(match: String): MatchSpec {
    val values = mutableMapOf<String, String>()
    match.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { token ->
            val parts = token.split("=", limit = 2)
            if (parts.size == 2) {
                values[parts[0].trim()] = parts[1].trim().trim('\'').trim('"')
            }
        }
    return MatchSpec(
        sender = values["sender"],
        path = values["path"],
        interfaceName = values["interface"],
        member = values["member"]
    )
}

private fun buildMatchRule(spec: MatchSpec): String = buildString {
    append("type='signal'")
    spec.sender?.let { append(",sender='$it'") }
    spec.path?.let { append(",path='$it'") }
    spec.interfaceName?.let { append(",interface='$it'") }
    spec.member?.let { append(",member='$it'") }
}

private fun WireMessage.matches(spec: MatchSpec): Boolean {
    if (spec.path != null && path != spec.path) return false
    if (spec.interfaceName != null && interfaceName != spec.interfaceName) return false
    if (spec.member != null && member != spec.member) return false
    if (spec.sender != null) {
        val senderOk = sender == spec.sender ||
            (spec.resolvedOwner != null && sender == spec.resolvedOwner)
        if (!senderOk) return false
    }
    return true
}

// Installs a signal subscription that receives ENTIRELY over the wire (epic #93 phase 3b):
// [rule] is registered on the bus via AddMatch and [spec] filters the incoming SIGNAL frames the
// reader delivers. Because the wire backend now emits signals over the wire too (see
// [emitWireSignal]), this single path covers BOTH cross-process senders (e.g. a dbusmock peer) and
// our OWN same-JVM served objects: each connection has its own socket + AddMatch, so the bus routes
// every matching signal to it exactly once. The in-process [LocalJvmSignalBus]/[LocalJvmMatchBus]
// is no longer used on this backend (it stays the dbus-java backend's same-process mechanism).
private fun installSignalMatch(
    wire: DBusWireConnection,
    rule: String,
    spec: MatchSpec,
    callback: MessageHandler
): Resource {
    runCatching { wire.addMatch(rule) }.getOrElse {
        throw createError(-1, "addMatch failed: ${it.message}")
    }
    val subscription = wire.onSignal { wireMessage ->
        if (!wireMessage.matches(spec)) return@onSignal
        val signal = signalFromMetadata(
            Message.Metadata(
                interfaceName = wireMessage.interfaceName,
                memberName = wireMessage.member,
                sender = wireMessage.sender,
                path = wireMessage.path,
                destination = wireMessage.destination,
                valid = true,
                empty = wireMessage.body.isEmpty()
            ).withLocalSenderCredentials(wireMessage.sender)
        )
        signal.payload.addAll(fromWireReplyValues(wireMessage.body, wireMessage.signature))
        JvmCurrentMessageContext.withMessage(signal) { callback(signal) }
    }
    val removed = AtomicBoolean(false)
    return com.monkopedia.sdbus.ActionResource {
        if (!removed.compareAndSet(false, true)) return@ActionResource
        runCatching { subscription.close() }
        runCatching { wire.removeMatch(rule) }
    }
}

// --- sender credentials (received signals) ---------------------------------------------------

private data class WireSenderCredentials(
    val pid: Int?,
    val uid: UInt?,
    val gid: UInt?,
    val supplementaryGids: List<UInt>?,
    val selinuxContext: String?
)

// Credentials of THIS process, used for senders that are one of our own connections (resolved via
// LocalJvmServiceRegistry). A signal that traversed the bus carries the authoritative sender (the
// bus stamps it), and for a same-process sender that is this very process -- so reporting the local
// process credentials is correct and matches the dbus-java backend's local short-circuit
// (resolveSenderCredentials -> localProcessCredentialsOrNull). Credentials of an EXTERNAL sender
// would require a GetConnectionCredentials bus call, which cannot run on the reader thread that
// delivers signals without deadlocking; no test needs it, so it is intentionally left out here.
private val localProcessWireCredentials: WireSenderCredentials by lazy {
    val pid = runCatching { ProcessHandle.current().pid().toInt() }.getOrNull()
    val unix = runCatching { com.sun.security.auth.module.UnixSystem() }.getOrNull()
    val selinuxContext = runCatching {
        java.nio.file.Files.readString(java.nio.file.Paths.get("/proc/self/attr/current"))
            .trim(' ', ' ')
            .ifEmpty { null }
    }.getOrNull()
    WireSenderCredentials(
        pid = pid,
        uid = unix?.uid?.toUInt(),
        gid = unix?.gid?.toUInt(),
        supplementaryGids = unix?.groups?.map { it.toUInt() },
        selinuxContext = selinuxContext
    )
}

private fun Message.Metadata.withLocalSenderCredentials(sender: String?): Message.Metadata {
    val senderName = sender?.takeIf { it.isNotBlank() } ?: return this
    if (LocalJvmServiceRegistry.resolveLocalUniqueName(senderName) == null) return this
    val creds = localProcessWireCredentials
    if (creds.pid == null &&
        creds.uid == null &&
        creds.gid == null &&
        creds.supplementaryGids == null &&
        creds.selinuxContext == null
    ) {
        return this
    }
    return copy(
        credsPid = creds.pid,
        credsUid = creds.uid,
        credsEuid = creds.uid,
        credsGid = creds.gid,
        credsEgid = creds.gid,
        credsSupplementaryGids = creds.supplementaryGids,
        selinuxContext = creds.selinuxContext
    )
}

// --- value-model <-> marshaller-model bridge -------------------------------------------------

private fun microsToMillis(micros: ULong): Long =
    ((micros + 999uL) / 1000uL).toLong().coerceAtLeast(1L)

/**
 * Converts a value from the high-level sdbus payload model into the model [DBusMarshaller]
 * consumes. The two models already share their representation (Strings, unsigned boxes,
 * [Message.JvmStructPayload]/[Message.JvmVariantPayload], [ObjectPath]/[Signature]/[UnixFd],
 * List/Map); the only normalization needed is turning a high-level [Variant] into a
 * [Message.JvmVariantPayload], recursively. The marshaller's own coercion handles the rest
 * (e.g. [ObjectPath]/[Signature] as strings, [UnixFd] as its int). The reply path needs NO
 * conversion: the demarshaller already produces decoder-compatible values.
 */
internal fun toWireBodyValue(value: Any?): Any? = when (value) {
    is Variant -> variantToPayload(value)
    // The string-like strong-name value classes arrive boxed in the payload (e.g. the
    // InterfaceName/PropertyName args of a Properties.Get call), since serialize() adds the raw
    // arg for a non-struct descriptor. Unwrap them to their String so the marshaller's `s`
    // handling accepts them, mirroring the dbus-java path's toJavaSignalValue. (ObjectPath,
    // Signature and UnixFd pass through: the marshaller coerces those itself.)
    is BusName -> value.value
    is InterfaceName -> value.value
    // MemberName covers its PropertyName/SignalName/MethodName typealiases (e.g. GetAll's
    // PropertyName map keys), which must unwrap to their String for the marshaller's `s` handling.
    is MemberName -> value.value
    is Message.JvmVariantPayload ->
        Message.JvmVariantPayload(value.signature, toWireBodyValue(value.value))
    is Message.JvmStructPayload ->
        Message.JvmStructPayload(value.signature, value.fields.map(::toWireBodyValue))
    is Map<*, *> -> value.entries.associate { (k, v) -> toWireBodyValue(k) to toWireBodyValue(v) }
    is List<*> -> value.map(::toWireBodyValue)
    is Array<*> -> value.map(::toWireBodyValue)
    else -> value
}

private fun variantToPayload(variant: Variant): Message.JvmVariantPayload {
    val signature = variant.peekValueType()
        ?: throw createError(-1, "Cannot marshal an empty variant")
    val message = createPlainMessage()
    variant.serializeTo(message)
    message.rewind(false)
    message.enterVariant(signature)
    val value = message.nextDeserializedValue("variantToPayload")
    message.exitVariant()
    return Message.JvmVariantPayload(signature, toWireBodyValue(value))
}

// --- reply/signal value-model bridge (wire -> decoder) ---------------------------------------

/**
 * Converts the demarshaller's wire value model into the canonical decoder-compatible model the
 * high-level deserializer expects -- the SAME model the dbus-java reply path produces
 * ([com.monkopedia.sdbus.internal.jvmdbus] fromJavaWireValue): a wire variant
 * ([Message.JvmVariantPayload]) becomes a high-level [Variant] (the decoder reads variants as
 * [Variant], never as the raw payload box); an object-path/signature that demarshalled to a bare
 * String becomes its strong [ObjectPath]/[Signature] type, so signature inference and grouped
 * multi-out (`(vo)` etc.) detection see the right element type instead of "s"; structs/arrays/
 * dicts recurse. The reply SIGNATURE header field is authoritative -- it is the only way to know a
 * String is `o`/`g` or what an element/entry type is -- so it drives the conversion. Without this
 * the reply body is NOT decoder-compatible (variants ClassCastException, `o` trips signature
 * enforcement); the demarshaller alone is signature-agnostic about these distinctions.
 */
internal fun fromWireReplyValues(body: List<Any?>, signature: String?): List<Any?> {
    val types = signature?.let(::splitTopLevelTypes).orEmpty()
    return body.mapIndexed { index, value -> fromWireReplyValue(value, types.getOrNull(index)) }
}

private fun fromWireReplyValue(value: Any?, signature: String?): Any? = when (value) {
    is Message.JvmVariantPayload -> wireVariant(value)
    is Message.JvmStructPayload -> {
        val fieldTypes = splitTopLevelTypes(value.signature.removeSurrounding("(", ")"))
        Message.JvmStructPayload(
            value.signature,
            value.fields.mapIndexed { i, field ->
                fromWireReplyValue(field, fieldTypes.getOrNull(i))
            }
        )
    }

    is Map<*, *> -> {
        val (keyType, valueType) = dictEntryTypes(signature)
        value.entries.associate { (k, v) ->
            fromWireReplyValue(k, keyType) to fromWireReplyValue(v, valueType)
        }
    }

    is List<*> -> {
        val elementType = signature?.takeIf { it.startsWith("a") }?.substring(1)
        value.map { fromWireReplyValue(it, elementType) }
    }

    is String -> when (signature) {
        "o" -> ObjectPath(value)
        "g" -> Signature(value)
        else -> value
    }

    else -> value
}

/**
 * Wraps a demarshalled wire variant in a high-level [Variant], converting its inner value
 * recursively so a nested variant/object-path is itself decoder-compatible once the variant is
 * unwrapped via `get()`. Mirrors PureJavaDbusBackend.fromJavaVariantValue.
 */
private fun wireVariant(payload: Message.JvmVariantPayload): Variant {
    val message = createPlainMessage()
    message.payload.add(
        Message.JvmVariantPayload(
            payload.signature,
            fromWireReplyValue(payload.value, payload.signature)
        )
    )
    return Variant().apply { deserializeFrom(message) }
}

/** Key/value element types of a dict signature `a{kv}`, or (null, null) if not a dict. */
private fun dictEntryTypes(signature: String?): Pair<String?, String?> {
    if (signature == null || !signature.startsWith("a{") || !signature.endsWith("}")) {
        return null to null
    }
    val parts = splitTopLevelTypes(signature.substring(2, signature.length - 1))
    return parts.getOrNull(0) to parts.getOrNull(1)
}

/** Splits a signature into its top-level complete types (e.g. "sa{sv}(is)" -> [s, a{sv}, (is)]). */
private fun splitTopLevelTypes(signature: String): List<String> {
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

/**
 * Wire signature for an outgoing body. Prefer the declared signature accumulated from serializer
 * descriptors (correct even for empty collections); fall back to value-based inference only when
 * no usable declared signature is available. Returns null for an empty body.
 */
private fun wireBodySignature(payload: List<Any?>, declaredSignature: String?): String? {
    if (payload.isEmpty()) return null
    if (!declaredSignature.isNullOrEmpty() &&
        countTopLevelTypes(declaredSignature) == payload.size
    ) {
        return declaredSignature
    }
    return payload.joinToString(separator = "") { value ->
        inferWireElementSignature(value)
            ?: throw createError(
                -1,
                "callMethod failed: unsupported argument type ${value?.let {
                    it::class.simpleName
                }}"
            )
    }
}

private fun inferWireElementSignature(value: Any?): String? = when (value) {
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
    is ObjectPath -> "o"
    is Signature -> "g"
    is UnixFd -> "h"
    is List<*> -> "a" + (value.firstNotNullOfOrNull(::inferWireElementSignature) ?: return "a")
    is Array<*> -> "a" + (value.firstNotNullOfOrNull(::inferWireElementSignature) ?: return "a")
    is Map<*, *> -> {
        val first = value.entries.firstOrNull() ?: return "a{}"
        val keySig = inferWireElementSignature(first.key) ?: return "a{}"
        val valueSig = inferWireElementSignature(first.value) ?: return "a{}"
        "a{$keySig$valueSig}"
    }
    else -> null
}

/** Counts the number of top-level complete types in a signature (e.g. "sa{sv}" -> 2). */
private fun countTopLevelTypes(signature: String?): Int {
    if (signature.isNullOrEmpty()) return 0
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
    var count = 0
    var index = 0
    while (index < signature.length) {
        val next = parseOne(index)
        if (next <= index) break
        count++
        index = next
    }
    return count
}
