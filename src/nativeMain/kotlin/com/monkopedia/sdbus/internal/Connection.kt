@file:OptIn(
    ExperimentalForeignApi::class, ExperimentalCoroutinesApi::class,
    ExperimentalNativeApi::class, ExperimentalNativeApi::class
)

package com.monkopedia.sdbus.internal

import cnames.structs.sd_bus
import cnames.structs.sd_bus_message
import cnames.structs.sd_bus_slot
import com.monkopedia.sdbus.BusName
import com.monkopedia.sdbus.IConnection.PollData
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MessageHandler
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MethodReply
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PlainMessage
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Signal
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.adopt_message
import com.monkopedia.sdbus.return_slot
import com.monkopedia.sdbus.return_slot_t
import com.monkopedia.sdbus.sdbusRequire
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.internal.NativePtr.Companion.NULL
import kotlin.native.ref.WeakReference
import kotlin.native.ref.createCleaner
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.placeTo
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import platform.linux.EFD_CLOEXEC
import platform.linux.EFD_NONBLOCK
import platform.linux.eventfd
import platform.linux.eventfd_read
import platform.linux.eventfd_write
import platform.posix.EINTR
import platform.posix.EINVAL
import platform.posix.POLLIN
import platform.posix.close
import platform.posix.errno
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.uint64_tVar
import sdbus.UINT64_MAX
import sdbus._SD_BUS_MESSAGE_TYPE_INVALID
import sdbus.sd_bus_error
import sdbus.sd_bus_interface_name_is_valid
import sdbus.sd_bus_member_name_is_valid
import sdbus.sd_bus_message_handler_t
import sdbus.sd_bus_object_path_is_valid
import sdbus.sd_bus_service_name_is_valid
import sdbus.sd_bus_vtable

internal typealias Bus = CPointer<sd_bus>
internal typealias BusFactory = (CPointer<CPointerVar<sd_bus>>) -> Int
internal typealias BusPtr = Reference<Bus?>

internal inline fun checkObjectPath(_PATH: String) = sdbusRequire(
    sd_bus_object_path_is_valid(_PATH) == 0,
    "Invalid object path '$_PATH' provided",
    EINVAL
)

internal inline fun checkInterfaceName(_NAME: String) = sdbusRequire(
    sd_bus_interface_name_is_valid(_NAME) == 0,
    "Invalid interface name '$_NAME' provided",
    EINVAL
)

internal inline fun checkServiceName(_NAME: String) = sdbusRequire(
    _NAME.isNotEmpty() && sd_bus_service_name_is_valid(_NAME) == 0,
    "Invalid service name '$_NAME' provided",
    EINVAL
)

internal inline fun checkMemberName(_NAME: String) = sdbusRequire(
    sd_bus_member_name_is_valid(_NAME) == 0,
    "Invalid member name '$_NAME' provided",
    EINVAL
)

private fun openBus(sdbus: ISdBus, busFactory: BusFactory): BusPtr {
    return memScoped {
        val bus = cValue<CPointerVar<sd_bus>>().getPointer(this)
        val r = busFactory(bus)
        sdbusRequire(r < 0, "Failed to open bus", -r)

        val busPtr = Reference(bus[0]) {
            sdbus.sd_bus_flush_close_unref(it)
        }
        finishHandshake(sdbus, busPtr.value)
        busPtr
    }
}

private fun openPseudoBus(sdbus: ISdBus): BusPtr = memScoped{
    val bus = cValue<CPointerVar<sd_bus>>().getPointer(this)

    val r = sdbus.sd_bus_new(bus)
    sdbusRequire(r < 0, "Failed to open pseudo bus", -r)

    sdbus.sd_bus_start(bus[0])
    // It is expected that sd_bus_start has failed here, returning -EINVAL, due to having
    // not set a bus address, but it will leave the bus in an OPENING state, which enables
    // us to create plain D-Bus messages as a local data storage (for Variant, for example),
    // without dependency on real IPC communication with the D-Bus broker daemon.
    sdbusRequire(r < 0 && r != -EINVAL, "Failed to start pseudo bus", -r)

    return Reference(bus[0]) {
        sdbus.sd_bus_close_unref(it)
    }
}

internal fun finishHandshake(sdbus: ISdBus, bus: CPointer<sd_bus>?) {
    // Process all requests that are part of the initial handshake,
    // like processing the Hello message response, authentication etc.,
    // to avoid connection authentication timeout in dbus daemon.
    memScoped {
        val pointer = bus
        require(pointer != null && pointer.rawValue != NULL) {
            "Invalid bus $pointer"
        }
        val r = sdbus.sd_bus_flush(pointer)

        sdbusRequire(r < 0, "Failed to flush bus on opening", -r)
    }
}


private fun pollfd.initFd(fd: Int, events: Short, revents: Short) {
    this.fd = fd
    this.events = events
    this.revents = revents
}

internal class Connection private constructor(
    private val sdbus: ISdBus,
    bus: BusPtr
) : IConnection {
    private val bus: BusPtr = bus
    private var asyncLoopThread: Job? = null
    val floatingMatchRules = mutableListOf<Resource>()
    private val eventThread = EventLoopThread(bus, sdbus)
    private val loopExitResource = Reference(eventThread.exitFd) {
        it.notify()
    }
    private var released = false

    constructor(intf: ISdBus, factory: BusFactory) : this(intf, openBus(intf, factory))

    override fun release() {
        loopExitResource.release()
        floatingMatchRules.forEach { it.release() }
        floatingMatchRules.clear()
        bus.release()
        released = true
    }

    suspend fun joinWithEventLoop() {
        require(!released) { "Connection has already been released" }
        asyncLoopThread?.join()
    }

    override fun requestName(name: ServiceName) {
        require(!released) { "Connection has already been released" }
        checkServiceName(name.value)

        val r = sdbus.sd_bus_request_name(bus.value, name.value, 0u)
        sdbusRequire(r < 0, "Failed to request bus name", -r)

        // In some cases we need to explicitly notify the event loop
        // to process messages that may have arrived while executing the call
        eventThread.wakeUpEventLoopIfMessagesInQueue()
    }

    override fun releaseName(name: ServiceName) {
        require(!released) { "Connection has already been released" }
        val r = sdbus.sd_bus_release_name(bus.value, name.value)
        sdbusRequire(r < 0, "Failed to release bus name", -r)

        // In some cases we need to explicitly notify the event loop
        // to process messages that may have arrived while executing the call
        eventThread.wakeUpEventLoopIfMessagesInQueue()
    }

    override fun getUniqueName(): BusName {
        require(!released) { "Connection has already been released" }
        memScoped {
            val name = cValue<CPointerVar<ByteVar>>().getPointer(this)
            val r = sdbus.sd_bus_get_unique_name(bus.value, name)
            val value = name[0]?.toKString()
            sdbusRequire(
                r < 0 || value == null,
                "Failed to get unique bus name",
                -r
            )
            return BusName(value!!)
        }
    }

    override fun enterEventLoopAsync() {
        require(!released) { "Connection has already been released" }
        if (asyncLoopThread == null) {
            // TODO: Create local scope
            val thiz = WeakReference(this)
            asyncLoopThread = eventThread.launch(GlobalScope).also {
                it.invokeOnCompletion {
                    thiz.get()?.asyncLoopThread = null
                }
            }
        }
    }

    override suspend fun leaveEventLoop() {
        require(!released) { "Connection has already been released" }
        eventThread.notifyEventLoopToExit()
        joinWithEventLoop()
    }

    override fun getEventLoopPollData(): PollData {
        require(!released) { "Connection has already been released" }
        return eventThread.getEventLoopPollData()
    }

    override fun addObjectManager(objectPath: ObjectPath) {
        require(!released) { "Connection has already been released" }
        val r = sdbus.sd_bus_add_object_manager(bus.value, null, objectPath.value)

        sdbusRequire(r < 0, "Failed to add object manager", -r)
    }

    override fun addObjectManager(
        objectPath: ObjectPath,
        return_slot: return_slot_t
    ): Reference<*> = memScoped {
        require(!released) { "Connection has already been released" }
        val slot = cValue<CPointerVar<sd_bus_slot>>().getPointer(this)

        val r = sdbus.sd_bus_add_object_manager(bus.value, slot, objectPath.value)

        sdbusRequire(r < 0, "Failed to add object manager", -r)

        Reference(slot[0]) {
            sdbus.sd_bus_slot_unref(it)
        }
    }

    override fun setMethodCallTimeout(timeout: Duration) {
        require(!released) { "Connection has already been released" }
        setMethodCallTimeout(timeout.inWholeMicroseconds.toULong())
    }

    override fun setMethodCallTimeout(timeout: ULong) {
        require(!released) { "Connection has already been released" }
        val r = sdbus.sd_bus_set_method_call_timeout(bus.value, timeout)

        sdbusRequire(r < 0, "Failed to set method call timeout", -r)
    }

    override fun getMethodCallTimeout(): ULong = memScoped {
        require(!released) { "Connection has already been released" }
        val timeout = cValue<uint64_tVar>().getPointer(this)

        val r = sdbus.sd_bus_get_method_call_timeout(bus.value, timeout)

        sdbusRequire(r < 0, "Failed to get method call timeout", -r)

        return timeout[0]
    }

    override fun addMatch(match: String, callback: MessageHandler) {
        require(!released) { "Connection has already been released" }
        floatingMatchRules.add(addMatch(match, callback, return_slot))
    }

    override fun addMatch(
        match: String,
        callback: MessageHandler,
        return_slot: return_slot_t
    ): Resource = memScoped {
        require(!released) { "Connection has already been released" }
        val slot = cValue<CPointerVar<sd_bus_slot>>().getPointer(this)
        val matchInfo = MatchInfo(callback, {}, WeakReference(this@Connection))
        val stableRef = StableRef.create(matchInfo)
        val bus = sdbus
        val r = bus.sd_bus_add_match(
            this@Connection.bus.value,
            slot,
            match,
            sdbus_match_callback,
            stableRef.asCPointer()
        )
        sdbusRequire(r < 0, "Failed to add match $match", -r)

        Reference(matchInfo to slot[0]) { (_, ref) ->
            bus.sd_bus_slot_unref(ref)
            stableRef.dispose()
        }

    }

    override fun addMatchAsync(
        match: String,
        callback: MessageHandler,
        installCallback: MessageHandler
    ) {
        require(!released) { "Connection has already been released" }
        floatingMatchRules.add(
            addMatchAsync(match, callback, installCallback, return_slot)
        )
    }

    override fun addMatchAsync(
        match: String,
        callback: MessageHandler,
        installCallback: MessageHandler,
        return_slot: return_slot_t
    ): Resource = memScoped {
        require(!released) { "Connection has already been released" }
        val slot = cValue<CPointerVar<sd_bus_slot>>().getPointer(this)
        val matchInfo = MatchInfo(callback, installCallback, WeakReference(this@Connection))
        val stableRef = StableRef.create(matchInfo)

        val r = sdbus.sd_bus_add_match_async(
            bus.value,
            slot,
            match,
            sdbus_match_callback,
            sdbus_match_install_callback,
            stableRef.asCPointer()
        )
        sdbusRequire(r < 0, "Failed to add match", -r)

        Reference(matchInfo to slot[0]) { (_, ref) ->
            sdbus.sd_bus_slot_unref(ref)
            stableRef.dispose()
        }
    }


    override fun getSdBusInterface(): ISdBus {
        return sdbus
    }

    override fun addObjectVTable(
        objectPath: ObjectPath,
        interfaceName: InterfaceName,
        vtable: CValuesRef<sd_bus_vtable>,
        userData: Any?,
        return_slot: return_slot_t
    ): Reference<*> = memScoped {
        require(!released) { "Connection has already been released" }
        val slot = cValue<CPointerVar<sd_bus_slot>>().getPointer(this)
        val ref = userData?.let { StableRef.create(it) }

        val r = sdbus.sd_bus_add_object_vtable(
            bus.get(), slot, objectPath.value, interfaceName.value, vtable, ref?.asCPointer()
        )

        sdbusRequire(r < 0, "Failed to register object vtable", -r)

        val cPointer = slot[0]
        Reference(cPointer) {
            sdbus.sd_bus_slot_unref(it)
            ref?.dispose()
        }
    }

    override fun createPlainMessage(): PlainMessage = memScoped {
        require(!released) { "Connection has already been released" }
        val sdbusMsg = cValue<CPointerVar<sd_bus_message>>().getPointer(this)

        val r =
            sdbus.sd_bus_message_new(bus.get(), sdbusMsg, _SD_BUS_MESSAGE_TYPE_INVALID.convert())

        sdbusRequire(r < 0, "Failed to create a plain message", -r)

        PlainMessage(sdbusMsg[0]!!, sdbus, adopt_message)
    }

    override fun createMethodCall(
        destination: ServiceName,
        objectPath: ObjectPath,
        interfaceName: InterfaceName,
        methodName: MethodName
    ): MethodCall =
        createMethodCall(destination.value, objectPath.value, interfaceName.value, methodName.value)

    override fun createMethodCall(
        destination: String,
        objectPath: String,
        interfaceName: String,
        methodName: String
    ): MethodCall = memScoped {
        require(!released) { "Connection has already been released" }
        val sdbusMsg = cValue<CPointerVar<sd_bus_message>>().getPointer(this)

        val r = sdbus.sd_bus_message_new_method_call(
            bus.get(),
            sdbusMsg,
            destination.takeIf { it.isNotEmpty() },
            objectPath,
            interfaceName,
            methodName
        )

        sdbusRequire(r < 0, "Failed to create method call", -r)

        MethodCall(sdbusMsg[0]!!, sdbus, adopt_message)
    }

    override fun createSignal(
        objectPath: ObjectPath,
        interfaceName: InterfaceName,
        signalName: SignalName
    ): Signal = createSignal(objectPath.value, interfaceName.value, signalName.value)

    override fun createSignal(
        objectPath: String,
        interfaceName: String,
        signalName: String
    ): Signal = memScoped {
        require(!released) { "Connection has already been released" }
        val sdbusMsg = cValue<CPointerVar<sd_bus_message>>().getPointer(this)

        val r = sdbus.sd_bus_message_new_signal(
            bus.get(),
            sdbusMsg,
            objectPath,
            interfaceName,
            signalName
        )

        sdbusRequire(r < 0, "Failed to create signal", -r)

        Signal(sdbusMsg[0]!!, sdbus, adopt_message)
    }

    override fun callMethod(message: MethodCall, timeout: ULong): MethodReply {
        require(!released) { "Connection has already been released" }
        // If the call expects reply, this call will block the bus connection from
        // serving other messages until the reply arrives or the call times out.
        val reply = message.send(timeout)

        // Wake up event loop to process messages that may have arrived in the meantime...
        eventThread.wakeUpEventLoopIfMessagesInQueue()

        return reply
    }

    override fun callMethod(
        message: MethodCall,
        callback: sd_bus_message_handler_t,
        userData: Any?,
        timeout: ULong,
        return_slot: return_slot_t
    ): Resource {
        require(!released) { "Connection has already been released" }
        // TODO: Think of ways of optimizing these three locking/unlocking of sdbus mutex (merge into one call?)
        val timeoutBefore = getEventLoopPollData().timeout ?: Duration.INFINITE
        val slot = message.send(callback, userData, timeout, return_slot_t)
        val timeoutAfter = getEventLoopPollData().timeout ?: Duration.INFINITE

        // An event loop may wait in poll with timeout `t1', while in another thread an async call is made with
        // timeout `t2'. If `t2' < `t1', then we have to wake up the event loop thread to update its poll timeout.
        if (timeoutAfter < timeoutBefore) {
            eventThread.notifyEventLoopToWakeUpFromPoll()
        }

        return slot
    }

    override fun emitPropertiesChangedSignal(
        objectPath: ObjectPath,
        interfaceName: InterfaceName,
        propNames: List<PropertyName>
    ) = emitPropertiesChangedSignal(objectPath.value, interfaceName.value, propNames)

    override fun emitPropertiesChangedSignal(
        objectPath: String,
        interfaceName: String,
        propNames: List<PropertyName>
    ) = memScoped {
        require(!released) { "Connection has already been released" }
        val names = if (propNames.isNotEmpty()) toStrv(propNames.map { it.value }) else null

        val r = sdbus.sd_bus_emit_properties_changed_strv(
            bus.get(),
            objectPath,
            interfaceName,
            names
        )

        sdbusRequire(r < 0, "Failed to emit PropertiesChanged signal", -r)
    }

    override fun emitInterfacesAddedSignal(objectPath: ObjectPath) {
        require(!released) { "Connection has already been released" }
        val r = sdbus.sd_bus_emit_object_added(bus.get(), objectPath.value)

        sdbusRequire(
            r < 0,
            "Failed to emit InterfacesAdded signal for all registered interfaces",
            -r
        )
    }

    override fun emitInterfacesAddedSignal(
        objectPath: ObjectPath,
        interfaces: List<InterfaceName>
    ) = memScoped {
        require(!released) { "Connection has already been released" }
        val names = if (interfaces.isNotEmpty()) toStrv(interfaces.map { it.value }) else null

        val r = sdbus.sd_bus_emit_interfaces_added_strv(bus.get(), objectPath.value, names)

        sdbusRequire(r < 0, "Failed to emit InterfacesAdded signal", -r)

    }

    override fun emitInterfacesRemovedSignal(objectPath: ObjectPath) {
        require(!released) { "Connection has already been released" }
        val r = sdbus.sd_bus_emit_object_removed(bus.get(), objectPath.value)

        sdbusRequire(
            r < 0,
            "Failed to emit InterfacesRemoved signal for all registered interfaces",
            -r
        )
    }

    override fun emitInterfacesRemovedSignal(
        objectPath: ObjectPath,
        interfaces: List<InterfaceName>
    ) = memScoped {
        require(!released) { "Connection has already been released" }
        val names = if (interfaces.isNotEmpty()) toStrv(interfaces.map { it.value }) else null

        val r = sdbus.sd_bus_emit_interfaces_removed_strv(bus.get(), objectPath.value, names)

        sdbusRequire(r < 0, "Failed to emit InterfacesRemoved signal", -r)
    }

    override fun registerSignalHandler(
        sender: String,
        objectPath: String,
        interfaceName: String,
        signalName: String,
        callback: sd_bus_message_handler_t,
        userData: Any?,
        return_slot: return_slot_t
    ): Resource = memScoped {
        require(!released) { "Connection has already been released" }
        val slot = cValue<CPointerVar<sd_bus_slot>>().getPointer(this)
        val ref = userData?.let { StableRef.create(it) }

        val r = sdbus.sd_bus_match_signal(
            bus.get(),
            slot,
            sender.takeIf { it.isNotEmpty() },
            objectPath.takeIf { it.isNotEmpty() },
            interfaceName.takeIf { it.isNotEmpty() },
            signalName.takeIf { it.isNotEmpty() },
            callback,
            ref?.asCPointer()
        )

        sdbusRequire(r < 0, "Failed to register signal handler", -r)

        Reference(slot[0]) {
            sdbus.sd_bus_slot_unref(it)
            ref?.dispose()
        }
    }

    override fun getCurrentlyProcessedMessage(): Message {
        require(!released) { "Connection has already been released" }
        val sdbusMsg = sdbus.sd_bus_get_current_message(bus.get())

        return Message(sdbusMsg!!, sdbus)
    }

    class EventFd(fd: Int = 0) {
        class FdHolder(var fd: Int) {
            val shouldCleanup = fd == 0

            init {
                if (fd == 0) {
                    fd = eventfd(0, EFD_CLOEXEC or EFD_NONBLOCK)
                    sdbusRequire(fd < 0, "Failed to create event object", -errno)
                }
            }
        }

        private val holder = FdHolder(fd)
        var fd: Int by holder::fd
        private val cleaner = createCleaner(holder) {
            if (it.shouldCleanup) {
                require(it.fd >= 0)
                close(it.fd)
            }
        }

        fun notify() {
            require(fd >= 0)
            val r = eventfd_write(fd, 1u)
            sdbusRequire(r < 0, "Failed to notify event descriptor", -errno)
        }

        fun clear(): Boolean = memScoped {
            require(fd >= 0)

            val value = cValue<uint64_tVar>().getPointer(this)
            val r = eventfd_read(fd, value)
            return r >= 0
        }
    }


    private data class MatchInfo(
        val callback: MessageHandler,
        val installCallback: MessageHandler,
        val connection: WeakReference<Connection>
    )

    private class EventLoopThread(
        private val bus_: BusPtr,
        private val sdbus_: ISdBus
    ) {
        private val loopExitFd_: EventFd = EventFd()
        private val eventFd_: EventFd = EventFd()

        val exitFd: EventFd
            get() = EventFd(loopExitFd_.fd)

        fun notifyEventLoopToExit() {
            loopExitFd_.notify()
        }

        fun notifyEventLoopToWakeUpFromPoll() {
            eventFd_.notify()
        }

        fun wakeUpEventLoopIfMessagesInQueue() {
            // When doing a sync call, other D-Bus messages may have arrived, waiting in the read queue.
            // In case an event loop is inside a poll in another thread, or an external event loop polls in the
            // same thread but as an unrelated event source, then we need to wake up the poll explicitly so the
            // event loop 1. processes all messages in the read queue, 2. updates poll timeout before next poll.
            if (arePendingMessagesInReadQueue())
                notifyEventLoopToWakeUpFromPoll()
        }

        val method: suspend CoroutineScope.() -> Unit = {
            enterEventLoop()
        }

        suspend fun enterEventLoop() {
            runCatching {
                while (true) {
                    // Process one pending event
                    processPendingEvent()

                    // And go to poll(), which wakes us up right away
                    // if there's another pending event, or sleeps otherwise.
                    val success = waitForNextEvent()
                    if (!success) break
                }
            }.onFailure { println(it.stackTraceToString()) }
        }

        fun processPendingEvent(): Boolean {
            val bus = bus_.get()
            require(bus != null)

            val r = sdbus_.sd_bus_process(bus, null)
            sdbusRequire(r < 0, "Failed to process bus requests", -r)

            // In correct use of sdbus-c++ API, r can be 0 only when processPendingEvent()
            // is called from an external event loop as a reaction to event fd being signalled.
            // If there are no more D-Bus messages to process, we know we have to clear event fd.
            if (r == 0)
                eventFd_.clear()

            return r > 0
        }

        suspend fun waitForNextEvent(): Boolean = memScoped {
            require(loopExitFd_.fd >= 0)
            require(eventFd_.fd >= 0)

            val sdbusPollData = getEventLoopPollData()
            val fdsCount = 3
            val fds = allocArray<pollfd>(fdsCount) { index: Int ->
                when (index) {
                    0 -> initFd(sdbusPollData.fd, sdbusPollData.events, 0)
                    1 -> initFd(eventFd_.fd, POLLIN.toShort(), 0)
                    else -> initFd(loopExitFd_.fd, POLLIN.toShort(), 0)
                }
            }

            val timeout = sdbusPollData.getPollTimeout()
            var r = poll(fds, fdsCount.convert(), timeout)

            if (r < 0 && errno == EINTR)
                return true // Try again

            sdbusRequire(r < 0, "Failed to wait on the bus", -errno)

            // Wake up notification, in order that we re-enter poll with freshly read PollData (namely, new poll timeout thereof)
            if ((fds[1].revents.toInt() and POLLIN) != 0) {
                val cleared = eventFd_?.clear()
                sdbusRequire(
                    cleared != true,
                    "Failed to read from the event descriptor",
                    -errno
                )
                // Go poll() again, but with up-to-date timeout (which will wake poll() up right away if there are messages to process)
                return waitForNextEvent()
            }
            // Loop exit notification
            if ((fds[2].revents.toInt() and POLLIN) != 0) {
                val cleared = loopExitFd_?.clear()
                sdbusRequire(
                    cleared != true,
                    "Failed to read from the loop exit descriptor",
                    -errno
                )
                return false
            }

            return true
        }

        fun getEventLoopPollData(): PollData {
            val pollData = ISdBus.PollData()
            val r = sdbus_.sd_bus_get_poll_data(bus_.value, pollData)
            sdbusRequire(r < 0, "Failed to get bus poll data", -r)

            require(eventFd_.fd >= 0)

            val timeout =
                if (pollData.timeout_usec == UINT64_MAX) Duration.INFINITE else pollData.timeout_usec.toLong().microseconds

            return PollData(pollData.fd, pollData.events, timeout, 0)//eventFd_.fd)
        }

        fun launch(scope: CoroutineScope): Job {
            return scope.launch {
                enterEventLoop()
            }
        }

        fun arePendingMessagesInReadQueue(): Boolean = memScoped {
            val readQueueSize = cValue<uint64_tVar>().getPointer(this)

            val r = sdbus_.sd_bus_get_n_queued_read(bus_.get(), readQueueSize)
            sdbusRequire(
                r < 0,
                "Failed to get number of pending messages in read queue",
                -r
            )

            return readQueueSize[0] > 0u
        }
    }

    companion object {
        val sdbus_match_install_callback =
            staticCFunction { sdbusMessage: CPointer<sd_bus_message>?, userData: COpaquePointer?, retError: CPointer<sd_bus_error>? ->
                val ok = invokeHandlerAndCatchErrors(retError) {
                    val matchInfo = userData?.asStableRef<MatchInfo>()?.get()
                    require(matchInfo != null)

                    val message = PlainMessage(
                        sdbusMessage!!,
                        matchInfo.connection.get()!!.getSdBusInterface()
                    )
                    matchInfo.installCallback(message)
                }
                if (ok) 0 else -1
            }
        val sdbus_match_callback =
            staticCFunction { sdbusMessage: CPointer<sd_bus_message>?, userData: COpaquePointer?, retError: CPointer<sd_bus_error>? ->
                val ok = invokeHandlerAndCatchErrors(retError) {
                    val matchInfo = userData?.asStableRef<MatchInfo>()?.get()
                    require(matchInfo != null)

                    val message = PlainMessage(
                        sdbusMessage!!,
                        matchInfo.connection.get()!!.getSdBusInterface()
                    )

                    matchInfo.callback(message)
                }
                if (ok) 0 else -1
            }

        private val eventPool = newFixedThreadPoolContext(8, "EventThreads")

        fun defaultConnection(intf: ISdBus) =
            Connection(intf) { intf.sd_bus_open(it) }

        fun systemConnection(intf: ISdBus) =
            Connection(intf) { intf.sd_bus_open_system(it) }

        fun sessionConnection(intf: ISdBus) =
            Connection(intf) { intf.sd_bus_open_user(it) }

        fun sessionConnection(intf: ISdBus, address: String) =
            Connection(intf) { intf.sd_bus_open_user_with_address(it, address) }

        fun remoteConnection(intf: ISdBus, host: String) =
            Connection(intf) { intf.sd_bus_open_system_remote(it, host) }

        fun privateConnection(intf: ISdBus, address: String) =
            Connection(intf) { intf.sd_bus_open_direct(it, address) }

        fun privateConnection(intf: ISdBus, fd: Int) =
            Connection(intf) { intf.sd_bus_open_direct(it, fd) }

        fun serverConnection(intf: ISdBus, fd: Int) =
            Connection(intf) { intf.sd_bus_open_server(it, fd) }

        fun Connection(intf: ISdBus, bus: CPointer<sd_bus>) =
            Connection(intf) { it.set(0, bus).let { 0 } }

        fun pseudoConnection(intf: ISdBus) =
            Connection(intf, openPseudoBus(intf))

    }

}


fun MemScope.toStrv(propNames: List<String>) =
    allocArray<CPointerVar<ByteVar>>(propNames.size + 1) {
        this.value =
            if (it == propNames.size) interpretCPointer(NULL)
            else propNames[it].cstr.placeTo(this@toStrv)
    }
