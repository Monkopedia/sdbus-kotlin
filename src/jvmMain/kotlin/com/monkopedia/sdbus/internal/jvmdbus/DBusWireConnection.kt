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

import com.monkopedia.sdbus.JvmUnixFdSupport
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.UnixFd
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlinx.coroutines.future.await
import org.newsclub.net.unix.AFUNIXSocket
import org.newsclub.net.unix.AFUNIXSocketAddress

/** Raised when SASL authentication with the bus fails. */
internal class DBusAuthException(message: String) : IOException(message)

/** Raised when a method call returns a D-Bus ERROR message. */
internal class DBusCallException(val errorName: String?, val errorMessage: String?) :
    RuntimeException(
        buildString {
            append(errorName ?: "org.freedesktop.DBus.Error.Failed")
            if (!errorMessage.isNullOrEmpty()) append(": ").append(errorMessage)
        }
    )

/**
 * Subscription handle for a signal listener registered via [DBusWireConnection.onSignal].
 * [close] removes the listener; it is idempotent.
 */
internal fun interface SignalSubscription : Closeable

/**
 * A low-level, self-owned D-Bus connection over a raw AF_UNIX socket (epic #93, phase 2).
 *
 * Responsibilities of this layer:
 * - parse the bus address and open a junixsocket [AFUNIXSocket];
 * - run the SASL EXTERNAL handshake (with `NEGOTIATE_UNIX_FD`) and `BEGIN`;
 * - frame messages on the wire via [WireMessageCodec] / [DBusMarshaller];
 * - run a single reader thread that routes replies to pending calls and signals to listeners;
 * - bootstrap the bus (`Hello` -> unique name) and offer `RequestName`/`ReleaseName`/`AddMatch`.
 *
 * It does NOT serve incoming method calls (phase 4) and is not yet wired into the production
 * client path (phase 3); it is exercised only by its own tests.
 *
 * Threading model:
 * - One daemon reader thread owns all reads. It demarshals each frame and dispatches:
 *   METHOD_RETURN/ERROR complete the [CompletableFuture] registered under their reply serial;
 *   SIGNAL is delivered to every registered listener; METHOD_CALL is ignored for now (phase 4).
 * - Writes are serialized under [writeLock]; any thread may call out concurrently.
 * - Outgoing calls take a monotonically increasing serial from [serialCounter] and await a
 *   [CompletableFuture] that the reader completes. [call] is suspend-friendly; bootstrap helpers
 *   use the blocking [callBlocking] so connect() need not be a coroutine.
 * - [close] stops the reader (closing the socket unblocks its read), joins the thread, and fails
 *   all outstanding calls — no threads or sockets are leaked.
 */
internal class DBusWireConnection private constructor(private val socket: AFUNIXSocket) :
    Closeable {

    private val input: InputStream = BufferedInputStream(socket.inputStream)
    private val output: OutputStream = socket.outputStream

    private val writeLock = Any()
    private val serialCounter = AtomicInteger(0)
    private val pending = ConcurrentHashMap<Int, CompletableFuture<WireMessage>>()
    private val signalListeners = CopyOnWriteArrayList<(WireMessage) -> Unit>()

    // Incoming METHOD_CALL handler (epic #93 phase 4: serving). When set, every incoming method
    // call is handed to it on a worker thread so a slow/async handler never stalls the reader.
    @Volatile
    private var incomingCallHandler: ((WireMessage) -> Unit)? = null

    // Worker pool for serving incoming calls. Off the reader thread so handler execution (and any
    // nested call a handler makes back on this same connection) cannot block reads or deadlock the
    // reader: the reader only enqueues, then keeps reading and completing pending-call futures.
    private val serveExecutor = Executors.newCachedThreadPool { runnable ->
        thread(start = false, isDaemon = true, name = "sdbus-jvm-wire-serve") { runnable.run() }
    }

    @Volatile
    private var uniqueNameValue: String? = null

    @Volatile
    var unixFdNegotiated: Boolean = false
        private set

    @Volatile
    private var running = false
    private var readerThread: Thread? = null

    /** The unique name assigned by the bus during [hello] (e.g. `:1.42`), or null before Hello. */
    val uniqueName: String? get() = uniqueNameValue

    val isReaderRunning: Boolean get() = readerThread?.isAlive == true

    // --- SASL EXTERNAL handshake ----------------------------------------------------------------

    private fun performSasl() {
        output.write(0) // mandatory leading NUL before the auth conversation
        output.flush()

        val uidHex = encodeHex(currentUid().toString().toByteArray(StandardCharsets.US_ASCII))
        writeLine("AUTH EXTERNAL $uidHex")
        val authResponse = readLine()
        when {
            authResponse.startsWith("OK ") -> Unit
            authResponse.startsWith("REJECTED") ->
                throw DBusAuthException("SASL EXTERNAL rejected: $authResponse")
            else -> throw DBusAuthException("Unexpected SASL response: $authResponse")
        }

        writeLine("NEGOTIATE_UNIX_FD")
        unixFdNegotiated = readLine().startsWith("AGREE_UNIX_FD")

        writeLine("BEGIN")
    }

    private fun writeLine(line: String) {
        output.write((line + "\r\n").toByteArray(StandardCharsets.US_ASCII))
        output.flush()
    }

    private fun readLine(): String {
        val buffer = ByteArrayOutputStream()
        var previous = -1
        while (true) {
            val c = input.read()
            if (c < 0) throw EOFException("End of stream during SASL")
            if (previous == '\r'.code && c == '\n'.code) {
                val bytes = buffer.toByteArray()
                return String(bytes, 0, bytes.size - 1, StandardCharsets.US_ASCII)
            }
            buffer.write(c)
            previous = c
        }
    }

    // --- reader thread --------------------------------------------------------------------------

    // FIFO of file descriptors received via SCM_RIGHTS, accessed only on the reader thread.
    // junixsocket delivers passed fds out-of-band alongside the message bytes; we drain them after
    // each frame and hand each message its declared UNIX_FDS count in arrival order.
    private val receivedFdQueue = ArrayDeque<FileDescriptor>()

    private fun startReader() {
        running = true
        readerThread = thread(start = true, isDaemon = true, name = "sdbus-jvm-wire-reader") {
            try {
                while (running) {
                    dispatch(resolveReceivedFds(WireMessageCodec.read(input)))
                }
            } catch (_: IOException) {
                // Expected on close() (socket closed) or peer disconnect.
            } catch (_: DBusMarshallingException) {
                // Malformed frame; treat as fatal for this connection.
            } finally {
                failAllPending(IOException("D-Bus connection closed"))
            }
        }
    }

    private fun dispatch(message: WireMessage) {
        when (message.type) {
            WireMessageType.METHOD_RETURN, WireMessageType.ERROR -> {
                val serial = message.replySerial
                if (serial != null) pending.remove(serial)?.complete(message)
            }

            WireMessageType.SIGNAL ->
                signalListeners.forEach { listener -> runCatching { listener(message) } }

            WireMessageType.METHOD_CALL -> {
                val handler = incomingCallHandler ?: return
                // Hand off to a worker so the reader thread keeps draining the socket; a handler
                // may itself call back on this connection and await a reply the reader delivers.
                runCatching {
                    serveExecutor.execute { runCatching { handler(message) } }
                }
            }
        }
    }

    private fun failAllPending(cause: Throwable) {
        val futures = pending.values.toList()
        pending.clear()
        futures.forEach { it.completeExceptionally(cause) }
    }

    // --- sending --------------------------------------------------------------------------------

    private fun nextSerial(): Int = serialCounter.incrementAndGet()

    private fun writeMessage(message: WireMessage) {
        // Collect any UnixFds in the body (depth-first, body order) and replace each with its
        // 0-based index into the attached fd array; the `h` wire value is that index, the actual
        // fds travel out-of-band via SCM_RIGHTS.
        val fds = mutableListOf<Int>()
        val outgoing = if (message.body.isEmpty()) {
            message
        } else {
            val body = message.body.map { collectOutboundFds(it, fds) }
            if (fds.isEmpty()) message else message.copy(body = body, unixFds = fds.size)
        }
        synchronized(writeLock) {
            if (fds.isEmpty()) {
                WireMessageCodec.write(output, outgoing)
                return
            }
            if (!unixFdNegotiated) {
                throw IOException(
                    "Cannot send ${fds.size} file descriptor(s): UNIX_FD passing was not negotiated"
                )
            }
            // Attach the fds to THIS message's bytes: setOutboundFileDescriptors arms the next
            // write on the socket, which we perform immediately under the write lock so the kernel
            // delivers the ancillary fds together with the framed message (D-Bus associates passed
            // fds with the message carrying UNIX_FDS).
            val descriptors = Array(fds.size) { JvmUnixFdSupport.descriptorForFd(fds[it]) }
            socket.setOutboundFileDescriptors(*descriptors)
            try {
                WireMessageCodec.write(output, outgoing)
            } finally {
                // junixsocket consumes pending outbound fds on the send; clear defensively so a
                // later fd-less write can never re-attach them.
                if (socket.hasOutboundFileDescriptors()) {
                    runCatching { socket.setOutboundFileDescriptors() }
                }
            }
        }
    }

    /**
     * Resolves the UNIX_FDS of a freshly read [message]: drains the fds junixsocket received
     * alongside it, then rewrites the `h` indices in the decoded body to [UnixFd]s wrapping the
     * corresponding received descriptors. A message with no UNIX_FDS is returned unchanged.
     */
    private fun resolveReceivedFds(message: WireMessage): WireMessage {
        val count = message.unixFds ?: return message
        if (count <= 0) return message
        drainReceivedFds()
        if (receivedFdQueue.size < count) {
            throw DBusMarshallingException(
                "Message declared $count unix fds but only ${receivedFdQueue.size} were received"
            )
        }
        val fds = ArrayList<UnixFd>(count)
        repeat(count) {
            fds.add(
                UnixFd.adopt(
                    JvmUnixFdSupport.adoptReceivedDescriptor(receivedFdQueue.removeFirst())
                )
            )
        }
        val types = message.signature?.let { DBusSignatureParser.parse(it) }.orEmpty()
        val body = message.body.mapIndexed { index, value ->
            resolveInboundFds(types.getOrNull(index), value, fds)
        }
        return message.copy(body = body)
    }

    private fun drainReceivedFds() {
        val received = socket.receivedFileDescriptors ?: return
        for (descriptor in received) receivedFdQueue.addLast(descriptor)
    }

    private data class PendingCall(val serial: Int, val future: CompletableFuture<WireMessage>)

    /**
     * Sends [request] (a serial is assigned automatically) and returns the registered future that
     * the reader completes with the matching reply, plus the serial so callers can evict the
     * pending entry if they give up waiting (timeout).
     */
    private fun dispatchCall(request: WireMessage): PendingCall {
        val serial = nextSerial()
        val future = CompletableFuture<WireMessage>()
        pending[serial] = future
        try {
            writeMessage(request.copy(serial = serial))
        } catch (e: IOException) {
            pending.remove(serial)
            future.completeExceptionally(e)
        }
        return PendingCall(serial, future)
    }

    /**
     * Suspends until the reply for [request] arrives or [timeoutMillis] elapses; throws
     * [DBusCallException] on a D-Bus error or on timeout. A timeout bounds the wait so a reply
     * that never arrives cannot hang the caller indefinitely (epic #93 phase 3).
     */
    suspend fun call(
        request: WireMessage,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
    ): WireMessage {
        val (serial, future) = dispatchCall(request)
        val reply = try {
            future.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS).await()
        } catch (e: TimeoutException) {
            pending.remove(serial)
            throw DBusCallException(
                TIMEOUT_ERROR_NAME,
                "Method call timed out after ${timeoutMillis}ms"
            )
        }
        throwIfError(reply)
        return reply
    }

    /** Blocking variant of [call], for bootstrap before any coroutine context exists. */
    fun callBlocking(
        request: WireMessage,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
    ): WireMessage {
        val (serial, future) = dispatchCall(request)
        val reply = try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            pending.remove(serial)
            throw DBusCallException(
                TIMEOUT_ERROR_NAME,
                "Method call timed out after ${timeoutMillis}ms"
            )
        }
        throwIfError(reply)
        return reply
    }

    /** Fire-and-forget send (e.g. a method call with NO_REPLY_EXPECTED, a signal, or a reply). */
    fun send(message: WireMessage): Int {
        val serial = nextSerial()
        writeMessage(message.copy(serial = serial))
        return serial
    }

    private fun throwIfError(reply: WireMessage) {
        if (reply.type == WireMessageType.ERROR) {
            throw DBusCallException(reply.errorName, reply.body.firstOrNull() as? String)
        }
    }

    // --- signal listeners -----------------------------------------------------------------------

    /** Registers [listener] for every incoming SIGNAL; close the returned handle to remove it. */
    fun onSignal(listener: (WireMessage) -> Unit): SignalSubscription {
        signalListeners.add(listener)
        return SignalSubscription { signalListeners.remove(listener) }
    }

    /**
     * Installs the handler that serves incoming METHOD_CALL messages (epic #93 phase 4). The
     * handler runs on a worker thread (never the reader) and is responsible for sending any reply
     * via [send]. At most one handler is installed per connection.
     */
    fun setIncomingCallHandler(handler: (WireMessage) -> Unit) {
        incomingCallHandler = handler
    }

    // --- bus bootstrap & standard calls ---------------------------------------------------------

    private fun hello() {
        val reply = callBlocking(
            WireMessage(
                type = WireMessageType.METHOD_CALL,
                destination = DBUS_SERVICE,
                path = DBUS_PATH,
                interfaceName = DBUS_INTERFACE,
                member = "Hello"
            )
        )
        uniqueNameValue = reply.body.firstOrNull() as? String
            ?: throw IOException("Hello reply did not contain a unique name")
    }

    /** Requests ownership of [busName] with the given RequestName [flags]; returns the result code. */
    fun requestName(busName: String, flags: UInt = 0u): Int {
        val reply = callBlocking(
            WireMessage(
                type = WireMessageType.METHOD_CALL,
                destination = DBUS_SERVICE,
                path = DBUS_PATH,
                interfaceName = DBUS_INTERFACE,
                member = "RequestName",
                signature = "su",
                body = listOf(busName, flags)
            )
        )
        return (reply.body.firstOrNull() as? UInt)?.toInt() ?: -1
    }

    /** Releases ownership of [busName]; returns the ReleaseName result code. */
    fun releaseName(busName: String): Int {
        val reply = callBlocking(
            WireMessage(
                type = WireMessageType.METHOD_CALL,
                destination = DBUS_SERVICE,
                path = DBUS_PATH,
                interfaceName = DBUS_INTERFACE,
                member = "ReleaseName",
                signature = "s",
                body = listOf(busName)
            )
        )
        return (reply.body.firstOrNull() as? UInt)?.toInt() ?: -1
    }

    /** Installs a match [rule] on the bus so matching broadcast signals reach this connection. */
    fun addMatch(rule: String) {
        callBlocking(
            WireMessage(
                type = WireMessageType.METHOD_CALL,
                destination = DBUS_SERVICE,
                path = DBUS_PATH,
                interfaceName = DBUS_INTERFACE,
                member = "AddMatch",
                signature = "s",
                body = listOf(rule)
            )
        )
    }

    /**
     * Resolves the current unique-name owner of a (possibly well-known) [busName], or null when
     * the name has no owner. Used to match incoming signals (whose sender is a unique name)
     * against a subscription expressed by a well-known destination.
     */
    fun getNameOwner(busName: String): String? = runCatching {
        callBlocking(
            WireMessage(
                type = WireMessageType.METHOD_CALL,
                destination = DBUS_SERVICE,
                path = DBUS_PATH,
                interfaceName = DBUS_INTERFACE,
                member = "GetNameOwner",
                signature = "s",
                body = listOf(busName)
            )
        ).body.firstOrNull() as? String
    }.getOrNull()

    /** Removes a previously installed match [rule]. */
    fun removeMatch(rule: String) {
        callBlocking(
            WireMessage(
                type = WireMessageType.METHOD_CALL,
                destination = DBUS_SERVICE,
                path = DBUS_PATH,
                interfaceName = DBUS_INTERFACE,
                member = "RemoveMatch",
                signature = "s",
                body = listOf(rule)
            )
        )
    }

    // --- shutdown -------------------------------------------------------------------------------

    override fun close() {
        running = false
        runCatching { serveExecutor.shutdownNow() }
        runCatching { socket.close() }
        readerThread?.let { reader ->
            if (reader !== Thread.currentThread()) {
                runCatching { reader.join(JOIN_TIMEOUT_MILLIS) }
            }
        }
        failAllPending(IOException("D-Bus connection closed"))
    }

    companion object {
        private const val DBUS_SERVICE = "org.freedesktop.DBus"
        private const val DBUS_PATH = "/org/freedesktop/DBus"
        private const val DBUS_INTERFACE = "org.freedesktop.DBus"
        private const val DEFAULT_TIMEOUT_MILLIS = 30_000L
        private const val JOIN_TIMEOUT_MILLIS = 2_000L
        private const val TIMEOUT_ERROR_NAME = "org.freedesktop.DBus.Error.Timeout"
        private const val DEFAULT_SYSTEM_BUS_ADDRESS = "unix:path=/var/run/dbus/system_bus_socket"

        // 4 KiB comfortably holds the cmsg for the kernel's SCM_MAX_FD (253) descriptors per recv.
        private const val ANCILLARY_RECEIVE_BUFFER_BYTES = 4096

        /** Connects to the session bus (`DBUS_SESSION_BUS_ADDRESS`) and completes Hello. */
        fun connectSession(): DBusWireConnection {
            val address = System.getenv("DBUS_SESSION_BUS_ADDRESS")
                ?: throw IOException("DBUS_SESSION_BUS_ADDRESS is not set")
            return connect(address)
        }

        /** Connects to the system bus (`DBUS_SYSTEM_BUS_ADDRESS` or the well-known default). */
        fun connectSystem(): DBusWireConnection {
            val address = System.getenv("DBUS_SYSTEM_BUS_ADDRESS") ?: DEFAULT_SYSTEM_BUS_ADDRESS
            return connect(address)
        }

        /** Connects to the bus at [address], runs SASL + Hello, and returns a ready connection. */
        fun connect(address: String): DBusWireConnection {
            val socketAddress = parseAddress(address)
            val socket = AFUNIXSocket.newInstance()
            socket.connect(socketAddress)
            // Size the ancillary buffer so SCM_RIGHTS descriptors passed with a message are
            // captured by the receiving recv() (D-Bus relays passed fds through the daemon). Sized
            // for the kernel's per-message SCM_MAX_FD ceiling with comfortable slack.
            runCatching { socket.ensureAncillaryReceiveBufferSize(ANCILLARY_RECEIVE_BUFFER_BYTES) }
            val connection = DBusWireConnection(socket)
            try {
                connection.performSasl()
                connection.startReader()
                connection.hello()
            } catch (e: Throwable) {
                connection.close()
                throw e
            }
            return connection
        }

        /**
         * Parses a D-Bus address string, supporting `unix:path=` and `unix:abstract=` (with an
         * optional `guid=` suffix) and a `;`-separated list of candidates.
         */
        fun parseAddress(address: String): AFUNIXSocketAddress {
            for (candidate in address.split(";")) {
                if (!candidate.startsWith("unix:")) continue
                var path: String? = null
                var abstract: String? = null
                for (pair in candidate.removePrefix("unix:").split(",")) {
                    val eq = pair.indexOf('=')
                    if (eq < 0) continue
                    when (pair.substring(0, eq)) {
                        "path" -> path = pair.substring(eq + 1)
                        "abstract" -> abstract = pair.substring(eq + 1)
                    }
                }
                if (abstract != null) return AFUNIXSocketAddress.inAbstractNamespace(abstract)
                if (path != null) return AFUNIXSocketAddress.of(File(path))
            }
            throw IOException("No usable unix transport in D-Bus address: $address")
        }

        private fun currentUid(): Long {
            // com.sun.security.auth.module.UnixSystem is in the jdk.security.auth module; reach it
            // reflectively so the build needs no explicit module wiring.
            val type = Class.forName("com.sun.security.auth.module.UnixSystem")
            val instance = type.getConstructor().newInstance()
            return type.getMethod("getUid").invoke(instance) as Long
        }

        private fun encodeHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
            for (b in bytes) append("%02x".format(b.toInt() and 0xff))
        }
    }
}

/**
 * Walks an outgoing body value, appending the raw fd of every [UnixFd] to [fds] (depth-first, in
 * body order) and replacing each with its 0-based index into that array. The marshaller then writes
 * the index as the `h` (UNIX_FD) value while the real fds ride along via SCM_RIGHTS. Containers
 * (structs, variants, arrays, dicts) recurse so fds nested anywhere in the body are handled.
 */
private fun collectOutboundFds(value: Any?, fds: MutableList<Int>): Any? = when (value) {
    is UnixFd -> {
        fds.add(value.fd)
        fds.size - 1
    }

    is Message.JvmStructPayload ->
        Message.JvmStructPayload(value.signature, value.fields.map { collectOutboundFds(it, fds) })

    is Message.JvmVariantPayload ->
        Message.JvmVariantPayload(value.signature, collectOutboundFds(value.value, fds))

    is Map<*, *> ->
        value.entries.associate { (k, v) ->
            collectOutboundFds(k, fds) to collectOutboundFds(v, fds)
        }

    is List<*> -> value.map { collectOutboundFds(it, fds) }
    is Array<*> -> value.map { collectOutboundFds(it, fds) }
    else -> value
}

/**
 * Signature-driven rewrite of a decoded incoming [value]: at every `h` position the demarshalled
 * index is replaced with `fds[index]` (the received descriptor wrapped as a [UnixFd]). The wire
 * value of an `h` is just an index, indistinguishable by type from an `i`, so the declared [type]
 * is required to find them; containers recurse using their own embedded signatures.
 */
private fun resolveInboundFds(type: DBusType?, value: Any?, fds: List<UnixFd>): Any? = when (type) {
    null -> value
    is DBusType.Basic -> if (type.type == 'h') {
        val index = (value as? Number)?.toInt()
            ?: throw DBusMarshallingException("Expected an fd index for 'h', got $value")
        fds.getOrNull(index)
            ?: throw DBusMarshallingException(
                "Unix fd index $index out of range (${fds.size} received)"
            )
    } else {
        value
    }

    is DBusType.ArrayType -> when (val element = type.element) {
        is DBusType.DictEntryType -> (value as? Map<*, *>)?.entries?.associate { (k, v) ->
            resolveInboundFds(element.key, k, fds) to resolveInboundFds(element.value, v, fds)
        } ?: value

        else -> (value as? List<*>)?.map { resolveInboundFds(element, it, fds) } ?: value
    }

    is DBusType.StructType -> {
        val payload = value as? Message.JvmStructPayload
        if (payload == null) {
            value
        } else {
            Message.JvmStructPayload(
                payload.signature,
                payload.fields.mapIndexed { i, field ->
                    resolveInboundFds(type.fields.getOrNull(i), field, fds)
                }
            )
        }
    }

    is DBusType.DictEntryType -> value // reached only via ArrayType above
    DBusType.VariantType -> {
        val payload = value as? Message.JvmVariantPayload
        if (payload == null) {
            value
        } else {
            val innerTypes = DBusSignatureParser.parse(payload.signature)
            Message.JvmVariantPayload(
                payload.signature,
                if (innerTypes.size == 1) {
                    resolveInboundFds(innerTypes.first(), payload.value, fds)
                } else {
                    payload.value
                }
            )
        }
    }
}
