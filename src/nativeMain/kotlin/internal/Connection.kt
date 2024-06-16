@file:OptIn(ExperimentalForeignApi::class, ExperimentalCoroutinesApi::class)

package com.monkopedia.sdbus.internal

import cnames.structs.sd_bus
import cnames.structs.sd_bus_message
import cnames.structs.sd_bus_slot
import cnames.structs.sd_event
import cnames.structs.sd_event_source
import com.monkopedia.sdbus.header.BusName
import com.monkopedia.sdbus.header.IConnection.PollData
import com.monkopedia.sdbus.header.InterfaceName
import com.monkopedia.sdbus.header.Message
import com.monkopedia.sdbus.header.MethodCall
import com.monkopedia.sdbus.header.MethodName
import com.monkopedia.sdbus.header.MethodReply
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.PlainMessage
import com.monkopedia.sdbus.header.PropertyName
import com.monkopedia.sdbus.header.SDBUS_THROW_ERROR_IF
import com.monkopedia.sdbus.header.ServiceName
import com.monkopedia.sdbus.header.Signal
import com.monkopedia.sdbus.header.SignalName
import com.monkopedia.sdbus.header.adopt_message
import com.monkopedia.sdbus.header.message_handler
import com.monkopedia.sdbus.header.return_slot
import com.monkopedia.sdbus.header.return_slot_t
import kotlin.native.internal.NativePtr.Companion.NULL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.microseconds
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.DeferScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cValuesOf
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import platform.linux.EFD_CLOEXEC
import platform.linux.EFD_NONBLOCK
import platform.linux.eventfd
import platform.linux.eventfd_read
import platform.linux.eventfd_write
import platform.posix.EINTR
import platform.posix.EINVAL
import platform.posix.POLLIN
import platform.posix.UINT64_MAX
import platform.posix.close
import platform.posix.errno
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.uint32_t
import platform.posix.uint64_tVar
import sdbus.SD_EVENT_OFF
import sdbus.SD_EVENT_ONESHOT
import sdbus._SD_BUS_MESSAGE_TYPE_INVALID
import sdbus.sd_bus_error
import sdbus.sd_bus_interface_name_is_valid
import sdbus.sd_bus_member_name_is_valid
import sdbus.sd_bus_message_handler_t
import sdbus.sd_bus_object_path_is_valid
import sdbus.sd_bus_service_name_is_valid
import sdbus.sd_bus_vtable
import sdbus.sd_event_source_set_enabled
import sdbus.sd_event_source_set_io_events
import sdbus.sd_event_source_set_time
import sdbus.uint64_t

private val DeferScope.asCoroutineScope: CoroutineScope
    get() {
        return CoroutineScope(SupervisorJob()).also {
            defer {
                it.cancel()
            }
        }
    }

typealias Bus = CPointer<sd_bus>
typealias BusFactory = (CPointer<CPointerVar<sd_bus>>) -> Int
typealias BusPtr = Reference<Bus?>
typealias Slot = Reference<*>

inline fun SDBUS_CHECK_OBJECT_PATH(_PATH: String) = SDBUS_THROW_ERROR_IF(
    sd_bus_object_path_is_valid(_PATH) == 0,
    "Invalid object path '$_PATH' provided",
    EINVAL
)

inline fun SDBUS_CHECK_INTERFACE_NAME(_NAME: String) = SDBUS_THROW_ERROR_IF(
    sd_bus_interface_name_is_valid(_NAME) == 0,
    "Invalid interface name '$_NAME' provided",
    EINVAL
)

inline fun SDBUS_CHECK_SERVICE_NAME(_NAME: String) = SDBUS_THROW_ERROR_IF(
    _NAME.isNotEmpty() && sd_bus_service_name_is_valid(_NAME) == 0,
    "Invalid service name '$_NAME' provided",
    EINVAL
)

inline fun SDBUS_CHECK_MEMBER_NAME(_NAME: String) = SDBUS_THROW_ERROR_IF(
    sd_bus_member_name_is_valid(_NAME) == 0,
    "Invalid member name '$_NAME' provided",
    EINVAL
)

private fun DeferScope.openBus(sdbus: ISdBus, busFactory: BusFactory): BusPtr {
    memScoped {
        val bus = cValue<CPointerVar<sd_bus>>().getPointer(this)
        val r = busFactory(bus);
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to open bus", -r)

        val busPtr = this@openBus.Reference(bus[0]) { sdbus.sd_bus_flush_close_unref(it) }
        finishHandshake(sdbus, busPtr.value)
        return busPtr;
    }
}


private fun DeferScope.openPseudoBus(sdbus: ISdBus): BusPtr {
    memScoped {
        val bus = cValue<CPointerVar<sd_bus>>().getPointer(this)

        val r = sdbus.sd_bus_new(bus)
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to open pseudo bus", -r);

        sdbus.sd_bus_start(bus[0]);
        // It is expected that sd_bus_start has failed here, returning -EINVAL, due to having
        // not set a bus address, but it will leave the bus in an OPENING state, which enables
        // us to create plain D-Bus messages as a local data storage (for Variant, for example),
        // without dependency on real IPC communication with the D-Bus broker daemon.
        SDBUS_THROW_ERROR_IF(r < 0 && r != -EINVAL, "Failed to start pseudo bus", -r);

        return this@openPseudoBus.Reference(bus[0]) { sdbus.sd_bus_close_unref(it) }
    }
}


fun finishHandshake(sdbus: ISdBus, bus: CPointer<sd_bus>?) {
    // Process all requests that are part of the initial handshake,
    // like processing the Hello message response, authentication etc.,
    // to avoid connection authentication timeout in dbus daemon.
    memScoped {
        val pointer = bus
        require(pointer != null && pointer.rawValue != NULL) {
            "Invalid bus $pointer"
        }
        val r = sdbus.sd_bus_flush(pointer)

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to flush bus on opening", -r);
    }
}


private fun pollfd.initFd(fd: Int, events: Short, revents: Short) {
    this.fd = fd
    this.events = events
    this.revents = revents
}

class Connection private constructor(
    initialScope: DeferScope,
    private val sdbus_: ISdBus,
    bus: Unowned<BusPtr>,
) : com.monkopedia.sdbus.header.Connection(initialScope), IConnection {
    private val loopExitFd_: EventFd = EventFd(scope)
    private val eventFd_: EventFd = EventFd(scope)
    private var sdEvent_: Connection.SdEvent? = null
    private val bus_: BusPtr = bus.own(scope)
    private var asyncLoopThread_: Job? = null
    val floatingMatchRules_ = mutableListOf<Slot>()

    constructor(initialScope: DeferScope, intf: ISdBus, factory: BusFactory)
        : this(initialScope, intf, create { openBus(intf, factory) })

    override fun onScopeCleared() {
        notifyEventLoopToExit()
    }

    override fun requestName(name: ServiceName) {
        SDBUS_CHECK_SERVICE_NAME(name.value);

        val r = sdbus_.sd_bus_request_name(bus_.value, name.value, 0u);
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to request bus name", -r);

        // In some cases we need to explicitly notify the event loop
        // to process messages that may have arrived while executing the call
        wakeUpEventLoopIfMessagesInQueue();
    }

    override fun releaseName(name: ServiceName) {
        val r = sdbus_.sd_bus_release_name(bus_.value, name.value);
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to release bus name", -r);

        // In some cases we need to explicitly notify the event loop
        // to process messages that may have arrived while executing the call
        wakeUpEventLoopIfMessagesInQueue()
    }

    override fun getUniqueName(): BusName {
        memScoped {

            val name = cValue<CPointerVar<ByteVar>>().getPointer(this)
            val r = sdbus_.sd_bus_get_unique_name(bus_.value, name);
            val value = name[0]?.toKString()
            SDBUS_THROW_ERROR_IF(
                r < 0 || value == null,
                "Failed to get unique bus name",
                -r
            );
            return BusName(value!!)
        }
    }

    override suspend fun enterEventLoop() {
        runCatching {
            while (true) {
                // Process one pending event
                processPendingEvent();

                // And go to poll(), which wakes us up right away
                // if there's another pending event, or sleeps otherwise.
                val success = waitForNextEvent();
                if (!success) break
            }
        }.onFailure { it.printStackTrace() }
        asyncLoopThread_ = null
    }

    override fun enterEventLoopAsync() {
        if (asyncLoopThread_ == null) {
            asyncLoopThread_ = scope.asCoroutineScope.launch(eventPool) {
                enterEventLoop()
            }
        }
    }

    override suspend fun leaveEventLoop() {
        notifyEventLoopToExit();
        joinWithEventLoop();
    }

    override fun getEventLoopPollData(): PollData {
        val pollData = ISdBus.PollData()
        val r = sdbus_.sd_bus_get_poll_data(bus_.value, pollData);
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to get bus poll data", -r);

        require(eventFd_.fd >= 0);

        val timeout =
            if (pollData.timeout_usec == UINT64_MAX) Duration.INFINITE else pollData.timeout_usec.toLong().microseconds;

        return PollData(pollData.fd, pollData.events, timeout, eventFd_.fd);
    }

    override fun addObjectManager(objectPath: ObjectPath) {
        val r = sdbus_.sd_bus_add_object_manager(bus_.value, null, objectPath.value)

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to add object manager", -r)
    }

    override fun addObjectManager(
        objectPath: ObjectPath,
        return_slot: return_slot_t
    ): Unowned<Slot> =
        create {
            val slot = cValue<CPointerVar<sd_bus_slot>>().getPointer(this)

            val r = sdbus_.sd_bus_add_object_manager(bus_.value, slot, objectPath.value)

            SDBUS_THROW_ERROR_IF(r < 0, "Failed to add object manager", -r);

            Reference(slot[0]) { sdbus_.sd_bus_slot_unref(it) }
        }

    override fun setMethodCallTimeout(timeout: Duration) {
        setMethodCallTimeout(timeout.inWholeMicroseconds.toULong())
    }

    override fun setMethodCallTimeout(timeout: ULong) {
        val r = sdbus_.sd_bus_set_method_call_timeout(bus_.value, timeout)

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to set method call timeout", -r)
    }

    override fun getMethodCallTimeout(): uint64_t = memScoped {
        val timeout = cValue<uint64_tVar>().getPointer(this)

        val r = sdbus_.sd_bus_get_method_call_timeout(bus_.value, timeout);

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to get method call timeout", -r);

        return timeout[0]
    }

    override fun addMatch(match: String, callback: message_handler) {
        floatingMatchRules_.add(addMatch(match, callback, return_slot).own(scope));
    }

    override fun addMatch(
        match: String,
        callback: message_handler,
        return_slot: return_slot_t
    ): Unowned<Slot> =
        create {
            val slot = cValue<CPointerVar<sd_bus_slot>>().getPointer(this)
            val matchInfo = MatchInfo(callback, {}, this@Connection, null)
            val stableRef = StableRef.create(matchInfo)

            val r = sdbus_.sd_bus_add_match(
                bus_.value,
                slot,
                match,
                sdbus_match_callback,
                stableRef.asCPointer()
            );
            SDBUS_THROW_ERROR_IF(r < 0, "Failed to add match $match", -r);

            matchInfo.slot = this@Connection.scope.Reference(slot[0]) { }

            Reference(matchInfo) {
                @Suppress("UNCHECKED_CAST")
                sdbus_.sd_bus_slot_unref(it.slot?.value as CValuesRef<sd_bus_slot>?)
                stableRef.dispose()
            }

        }

    override fun addMatchAsync(
        match: String,
        callback: message_handler,
        installCallback: message_handler
    ) {
        floatingMatchRules_.add(
            addMatchAsync(match, callback, installCallback, return_slot).own(scope)
        )
    }

    override fun addMatchAsync(
        match: String,
        callback: message_handler,
        installCallback: message_handler,
        return_slot: return_slot_t
    ): Unowned<Slot> = create {
        val slot = cValue<CPointerVar<sd_bus_slot>>().getPointer(this)
        val matchInfo = MatchInfo(callback, installCallback, this@Connection, null)
        val stableRef = StableRef.create(matchInfo)

        val r = sdbus_.sd_bus_add_match_async(
            bus_.value,
            slot,
            match,
            sdbus_match_callback,
            sdbus_match_install_callback,
            stableRef.asCPointer()
        );
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to add match", -r);

        matchInfo.slot = this@Connection.scope.Reference(slot[0]) { }

        Reference(matchInfo) {
            @Suppress("UNCHECKED_CAST")
            sdbus_.sd_bus_slot_unref(it.slot?.value as CValuesRef<sd_bus_slot>?)
            stableRef.dispose()
        }
    }

    override fun detachSdEventLoop() {
        sdEvent_?.reset()
    }

    override fun getSdEventLoop(): CPointer<sd_event>? {
        @Suppress("UNCHECKED_CAST")
        return sdEvent_?.sdEvent?.value as? CPointer<sd_event>
    }

    override fun getSdBusInterface(): ISdBus {
        return sdbus_
    }

    override fun addObjectVTable(
        objectPath: ObjectPath,
        interfaceName: InterfaceName,
        vtable: CValuesRef<sd_bus_vtable>,
        userData: Any?,
        return_slot: return_slot_t
    ): Unowned<Slot> = create {
        val slot = cValue<CPointerVar<sd_bus_slot>>().getPointer(this)
        val ref = userData?.let { StableRef.create(it) }

        val r = sdbus_.sd_bus_add_object_vtable(
            bus_.get(), slot, objectPath.value, interfaceName.value, vtable, ref?.asCPointer()
        );

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to register object vtable", -r);

        Reference(cValuesOf(slot[0])) {
            sdbus_.sd_bus_slot_unref(it.ptr[0])
            ref?.dispose()
        }

    }

    override fun createPlainMessage(): PlainMessage = memScoped {
        val sdbusMsg = cValue<CPointerVar<sd_bus_message>>().getPointer(this)

        val r =
            sdbus_.sd_bus_message_new(bus_.get(), sdbusMsg, _SD_BUS_MESSAGE_TYPE_INVALID.convert())

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to create a plain message", -r);

        PlainMessage(sdbusMsg[0]!!, sdbus_, adopt_message);
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
        val sdbusMsg = cValue<CPointerVar<sd_bus_message>>().getPointer(this)

        val r = sdbus_.sd_bus_message_new_method_call(
            bus_.get(),
            sdbusMsg,
            destination.takeIf { it.isNotEmpty() },
            objectPath,
            interfaceName,
            methodName
        );

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to create method call", -r);

        MethodCall(sdbusMsg[0]!!, sdbus_, adopt_message)
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
        val sdbusMsg = cValue<CPointerVar<sd_bus_message>>().getPointer(this)

        val r = sdbus_.sd_bus_message_new_signal(
            bus_.get(),
            sdbusMsg,
            objectPath,
            interfaceName,
            signalName
        );

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to create signal", -r)

        Signal(sdbusMsg[0]!!, sdbus_, adopt_message);
    }

    override fun callMethod(message: MethodCall, timeout: ULong): MethodReply {
        // If the call expects reply, this call will block the bus connection from
        // serving other messages until the reply arrives or the call times out.
        val reply = message.send(timeout)

        // Wake up event loop to process messages that may have arrived in the meantime...
        wakeUpEventLoopIfMessagesInQueue()

        return reply
    }

    override fun callMethod(
        message: MethodCall,
        callback: sd_bus_message_handler_t,
        userData: Any?,
        timeout: ULong,
        return_slot: return_slot_t
    ): Unowned<Slot> {
        // TODO: Think of ways of optimizing these three locking/unlocking of sdbus mutex (merge into one call?)
        val timeoutBefore = getEventLoopPollData().timeout ?: Duration.INFINITE
        val slot = message.send(callback, userData, timeout, return_slot_t);
        val timeoutAfter = getEventLoopPollData().timeout ?: Duration.INFINITE

        // An event loop may wait in poll with timeout `t1', while in another thread an async call is made with
        // timeout `t2'. If `t2' < `t1', then we have to wake up the event loop thread to update its poll timeout.
        if (timeoutAfter < timeoutBefore) {
            notifyEventLoopToWakeUpFromPoll()
        }

        return slot;
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
        val names = if (propNames.isNotEmpty()) toStrv(propNames.map { it.value }) else null

        val r = sdbus_.sd_bus_emit_properties_changed_strv(
            bus_.get(),
            objectPath,
            interfaceName,
            names
        )

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to emit PropertiesChanged signal", -r);
    }

    override fun emitInterfacesAddedSignal(objectPath: ObjectPath) {
        val r = sdbus_.sd_bus_emit_object_added(bus_.get(), objectPath.value);

        SDBUS_THROW_ERROR_IF(
            r < 0,
            "Failed to emit InterfacesAdded signal for all registered interfaces",
            -r
        )
    }

    override fun emitInterfacesAddedSignal(
        objectPath: ObjectPath,
        interfaces: List<InterfaceName>
    ) = memScoped {
        val names = if (interfaces.isNotEmpty()) toStrv(interfaces.map { it.value }) else null

        println("Emitting interface added")
        val r = sdbus_.sd_bus_emit_interfaces_added_strv(bus_.get(), objectPath.value, names)
        println("Emitted interface added signal $objectPath $interfaces $r")

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to emit InterfacesAdded signal", -r)

    }

    override fun emitInterfacesRemovedSignal(objectPath: ObjectPath) {
        val r = sdbus_.sd_bus_emit_object_removed(bus_.get(), objectPath.value);

        SDBUS_THROW_ERROR_IF(
            r < 0,
            "Failed to emit InterfacesRemoved signal for all registered interfaces",
            -r
        )
    }

    override fun emitInterfacesRemovedSignal(
        objectPath: ObjectPath,
        interfaces: List<InterfaceName>
    ) = memScoped {
        val names = if (interfaces.isNotEmpty()) toStrv(interfaces.map { it.value }) else null

        val r = sdbus_.sd_bus_emit_interfaces_removed_strv(bus_.get(), objectPath.value, names)

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to emit InterfacesRemoved signal", -r);
    }

    override fun registerSignalHandler(
        sender: String,
        objectPath: String,
        interfaceName: String,
        signalName: String,
        callback: sd_bus_message_handler_t,
        userData: Any?,
        return_slot: return_slot_t
    ): Unowned<Slot> = create {
        val slot = cValue<CPointerVar<sd_bus_slot>>().getPointer(this)
        val ref = userData?.let { StableRef.create(it) }

        val r = sdbus_.sd_bus_match_signal(
            bus_.get(),
            slot,
            sender.takeIf { it.isNotEmpty() },
            objectPath.takeIf { it.isNotEmpty() },
            interfaceName.takeIf { it.isNotEmpty() },
            signalName.takeIf { it.isNotEmpty() },
            callback,
            ref?.asCPointer()
        );

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to register signal handler", -r);

        Reference(slot[0]) {
            sdbus_.sd_bus_slot_unref(it)
            ref?.dispose()
        }
    }

    fun notifyEventLoopToExit() {
        loopExitFd_.notify()
    }

    fun notifyEventLoopToWakeUpFromPoll() {
        eventFd_.notify();
    }

    fun wakeUpEventLoopIfMessagesInQueue() {
        // When doing a sync call, other D-Bus messages may have arrived, waiting in the read queue.
        // In case an event loop is inside a poll in another thread, or an external event loop polls in the
        // same thread but as an unrelated event source, then we need to wake up the poll explicitly so the
        // event loop 1. processes all messages in the read queue, 2. updates poll timeout before next poll.
        if (arePendingMessagesInReadQueue())
            notifyEventLoopToWakeUpFromPoll();
    }

    suspend fun joinWithEventLoop() {
        asyncLoopThread_?.join()
    }

    override fun processPendingEvent(): Boolean {
        val bus = bus_.get();
        require(bus != null);

        val r = sdbus_.sd_bus_process(bus, null);
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to process bus requests", -r);

        // In correct use of sdbus-c++ API, r can be 0 only when processPendingEvent()
        // is called from an external event loop as a reaction to event fd being signalled.
        // If there are no more D-Bus messages to process, we know we have to clear event fd.
        if (r == 0)
            eventFd_.clear();

        return r > 0;
    }

    suspend fun waitForNextEvent(): Boolean = memScoped {
        require(loopExitFd_.fd >= 0)
        require(eventFd_.fd >= 0)

        val sdbusPollData = getEventLoopPollData();
        val fdsCount = 3
        val fds = allocArray<pollfd>(fdsCount) { index: Int ->
            when (index) {
                0 -> initFd(sdbusPollData.fd, sdbusPollData.events, 0)
                1 -> initFd(eventFd_.fd, POLLIN.toShort(), 0)
                else -> initFd(loopExitFd_.fd, POLLIN.toShort(), 0)
            }
        }

        val timeout = sdbusPollData.getPollTimeout();
        var r = poll(fds, fdsCount.convert(), timeout);

        if (r < 0 && errno == EINTR)
            return true // Try again

        SDBUS_THROW_ERROR_IF(r < 0, "Failed to wait on the bus", -errno);

        // Wake up notification, in order that we re-enter poll with freshly read PollData (namely, new poll timeout thereof)
        if ((fds[1].revents.toInt() and POLLIN) != 0) {
            val cleared = eventFd_?.clear()
            SDBUS_THROW_ERROR_IF(
                cleared != true,
                "Failed to read from the event descriptor",
                -errno
            );
            // Go poll() again, but with up-to-date timeout (which will wake poll() up right away if there are messages to process)
            return waitForNextEvent()
        }
        // Loop exit notification
        if ((fds[2].revents.toInt() and POLLIN) != 0) {
            val cleared = loopExitFd_?.clear()
            SDBUS_THROW_ERROR_IF(
                cleared != true,
                "Failed to read from the loop exit descriptor",
                -errno
            )
            return false
        }

        return true
    }

    fun arePendingMessagesInReadQueue(): Boolean = memScoped {
        val readQueueSize = cValue<uint64_tVar>().getPointer(this)

        val r = sdbus_.sd_bus_get_n_queued_read(bus_.get(), readQueueSize);
        SDBUS_THROW_ERROR_IF(r < 0, "Failed to get number of pending messages in read queue", -r);

        return readQueueSize[0] > 0u;
    }

    override fun getCurrentlyProcessedMessage(): Message {
        val sdbusMsg = sdbus_.sd_bus_get_current_message(bus_.get());

        return Message(sdbusMsg!!, sdbus_);
    }

    class EventFd(scope: DeferScope, var fd: Int = 0) : ScopedObject(scope) {

        init {
            fd = eventfd(0, EFD_CLOEXEC or EFD_NONBLOCK)
            SDBUS_THROW_ERROR_IF(fd < 0, "Failed to create event object", -errno)
        }

        override fun onLeaveScopes() {
            require(fd >= 0)
            close(fd)
        }

        fun notify() {
            require(fd >= 0)
            val r = eventfd_write(fd, 1u)
            SDBUS_THROW_ERROR_IF(r < 0, "Failed to notify event descriptor", -errno);
        }

        fun clear(): Boolean = memScoped {
            require(fd >= 0);

            val value = cValue<uint64_tVar>().getPointer(this)
            val r = eventfd_read(fd, value)
            return r >= 0;
        }
    }


    data class MatchInfo(
        val callback: message_handler,
        val installCallback: message_handler,
        val connection: Connection,
        var slot: Slot? = null,
    )

    // sd-event integration
    class SdEvent(
        sdEvent: Unowned<Slot>,
        sdTimeEventSource: Unowned<Slot>,
        sdIoEventSource: Unowned<Slot>,
        sdInternalEventSource: Unowned<Slot>,
        initialScope: DeferScope
    ) : Scope(initialScope) {
        val sdEvent: Slot = sdEvent.own(scope)
        val sdTimeEventSource: Slot = sdTimeEventSource.own(scope)
        val sdIoEventSource: Slot = sdIoEventSource.own(scope)
        val sdInternalEventSource: Slot = sdInternalEventSource.own(scope)

        fun reset() {
        }
    }

    companion object {
        val sdbus_match_install_callback =
            staticCFunction { sdbusMessage: CPointer<sd_bus_message>?, userData: COpaquePointer?, retError: CPointer<sd_bus_error>? ->
                val ok = invokeHandlerAndCatchErrors(retError) {
                    memScoped {
                        val matchInfo = userData?.asStableRef<MatchInfo>()?.get()
                        require(matchInfo != null);

                        val message = PlainMessage(
                            sdbusMessage!!,
                            matchInfo.connection.getSdBusInterface()
                        )
                        runBlocking {
                            matchInfo.installCallback(message)
                        }
                    }
                }
                if (ok) 0 else -1
            }
        val sdbus_match_callback =
            staticCFunction { sdbusMessage: CPointer<sd_bus_message>?, userData: COpaquePointer?, retError: CPointer<sd_bus_error>? ->
                val ok = invokeHandlerAndCatchErrors(retError) {
                    memScoped {

                        val matchInfo = userData?.asStableRef<MatchInfo>()?.get()
                        require(matchInfo != null);

                        val message = PlainMessage(
                            sdbusMessage!!,
                            matchInfo.connection.getSdBusInterface()
                        )

                        runBlocking {
                            matchInfo.callback(message)
                        }
                    }

                }
                if (ok) 0 else -1
            }


        val onSdTimerEvent =
            staticCFunction { s: CPointer<sd_event_source>? /*s*/, usec: uint64_t /*usec*/, userdata: COpaquePointer? ->
                val connection = userdata?.asStableRef<Connection>()?.get()
                require(connection != null);

                connection.processPendingEvent();

                1
            }

        val onSdIoEvent =
            staticCFunction { s: CPointer<sd_event_source>?, fd: Int, revents: uint32_t, userdata: COpaquePointer? ->
                val connection = userdata?.asStableRef<Connection>()?.get()
                require(connection != null);

                connection.processPendingEvent();

                1
            }

        val onSdInternalEvent =
            staticCFunction { s: CPointer<sd_event_source>?, fd: Int, revents: uint32_t, userdata: COpaquePointer? ->
                val connection = userdata?.asStableRef<Connection>()?.get()
                require(connection != null);

                // It's not really necessary to processPendingEvent() here. We just clear the event fd.
                // The sd-event loop will before the next poll call prepare callbacks for all event sources,
                // including I/O bus fd. This will get up-to-date poll timeout, which will be zero if there
                // are pending D-Bus messages in the read queue, which will immediately wake up next poll
                // and go to onSdIoEvent() handler, which calls processPendingEvent(). Viola.
                // For external event loops that only have access to public sdbus-c++ API, processPendingEvent()
                // is the only option to clear event fd (it comes at a little extra cost but on the other hand
                // the solution is simpler for clients -- we don't provide an extra method for just clearing
                // the event fd. There is one method for both fd's -- and that's processPendingEvent().

                // Kept here so that potential readers know what to do in their custom external event loops.
                //(void)connection->processPendingEvent();

                connection.eventFd_.clear();

                1
            }

        val onSdEventPrepare =
            staticCFunction { s: CPointer<sd_event_source>?, userdata: COpaquePointer? ->
                val connection = userdata?.asStableRef<Connection>()?.get()
                require(connection != null);

                val sdbusPollData = connection.getEventLoopPollData();

                // Set poll events to watch out for on I/O fd
                @Suppress("UNCHECKED_CAST") val sdIoEventSource =
                    connection.sdEvent_?.sdIoEventSource?.get() as? CValuesRef<sd_event_source>
                var r =
                    sd_event_source_set_io_events(sdIoEventSource, sdbusPollData.events.convert())
                SDBUS_THROW_ERROR_IF(r < 0, "Failed to set poll events for IO event source", -r);

                // Set poll events to watch out for on internal event fd
                @Suppress("UNCHECKED_CAST") val sdInternalEventSource =
                    connection.sdEvent_?.sdInternalEventSource?.get() as? CValuesRef<sd_event_source>
                r = sd_event_source_set_io_events(sdInternalEventSource, POLLIN.convert());
                SDBUS_THROW_ERROR_IF(
                    r < 0,
                    "Failed to set poll events for internal event source",
                    -r
                );

                // Set current timeout to the time event source (it may be zero if there are messages in the sd-bus queues to be processed)
                @Suppress("UNCHECKED_CAST") val sdTimeEventSource =
                    connection.sdEvent_?.sdTimeEventSource?.get() as? CValuesRef<sd_event_source>
                r = sd_event_source_set_time(
                    sdTimeEventSource,
                    (sdbusPollData.timeout?.inWholeMicroseconds ?: 0).convert()
                );
                SDBUS_THROW_ERROR_IF(r < 0, "Failed to set timeout for time event source", -r);
                // In case the timeout is infinite, we disable the timer in the sd_event loop.
                // This prevents a syscall error, where `timerfd_settime` returns `EINVAL`,
                // because the value is too big. See #324 for details
                r = sd_event_source_set_enabled(
                    sdTimeEventSource,
                    if (sdbusPollData.timeout != INFINITE) SD_EVENT_ONESHOT else SD_EVENT_OFF
                );
                SDBUS_THROW_ERROR_IF(r < 0, "Failed to enable time event source", -r);

                1
            }

        private val eventPool = newFixedThreadPoolContext(8, "EventThreads")

        fun DeferScope.defaultConnection(intf: ISdBus) =
            Connection(this, intf) { intf.sd_bus_open(it) }

        fun DeferScope.systemConnection(intf: ISdBus) =
            Connection(this, intf) { intf.sd_bus_open_system(it) }

        fun DeferScope.sessionConnection(intf: ISdBus) =
            Connection(this, intf) { intf.sd_bus_open_user(it) }

        fun DeferScope.sessionConnection(intf: ISdBus, address: String) =
            Connection(this, intf) { intf.sd_bus_open_user_with_address(it, address) }

        fun DeferScope.remoteConnection(intf: ISdBus, host: String) =
            Connection(this, intf) { intf.sd_bus_open_system_remote(it, host) }

        fun DeferScope.privateConnection(intf: ISdBus, address: String) =
            Connection(this, intf) { intf.sd_bus_open_direct(it, address) }

        fun DeferScope.privateConnection(intf: ISdBus, fd: Int) =
            Connection(this, intf) { intf.sd_bus_open_direct(it, fd) }

        fun DeferScope.serverConnection(intf: ISdBus, fd: Int) =
            Connection(this, intf) { intf.sd_bus_open_server(it, fd) }

        fun DeferScope.Connection(intf: ISdBus, bus: CPointer<sd_bus>) =
            Connection(this, intf) { it.set(0, bus).let { 0 } }

        fun DeferScope.pseudoConnection(intf: ISdBus) =
            Connection(this, intf, create { openPseudoBus(intf) })

    }

};


fun MemScope.toStrv(propNames: List<String>) =
    allocArray<CPointerVar<ByteVar>>(propNames.size + 1) {
        this.value =
            if (it == propNames.size) interpretCPointer(NULL)
            else propNames[it].cstr.placeTo(this@toStrv)
    }
