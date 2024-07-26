@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodReply
import com.monkopedia.sdbus.ObjectManagerProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.setProperty
import com.monkopedia.sdbus.toError
import kotlin.time.Duration
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class ObjectManagerTestProxy(proxy: IProxy) : ObjectManagerProxy {
    override val proxy: IProxy = proxy

    constructor(
        connection: com.monkopedia.sdbus.IConnection,
        destination: ServiceName,
        objectPath: ObjectPath
    ) : this(createProxy(connection, destination, objectPath))

    init {
        registerObjectManagerProxy()
    }

    override fun onInterfacesAdded(
        objectPath: ObjectPath,
        interfacesAndProperties: Map<InterfaceName, Map<PropertyName, Variant>>
    ) {
        m_onInterfacesAddedHandler?.invoke(objectPath, interfacesAndProperties)
    }

    override fun onInterfacesRemoved(objectPath: ObjectPath, interfaces: List<InterfaceName>) {
        m_onInterfacesRemovedHandler?.invoke(objectPath, interfaces)
    }

    var m_onInterfacesAddedHandler: (
        (ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>) -> Unit
    )? =
        null
    var m_onInterfacesRemovedHandler: ((ObjectPath, List<InterfaceName>) -> Unit)? = null
}

class TestProxy private constructor(proxy: IProxy) : IntegrationTestsProxy(proxy) {

    constructor(destination: ServiceName, objectPath: ObjectPath) : this(
        createProxy(destination, objectPath)
    )

    constructor(
        destination: ServiceName,
        objectPath: ObjectPath,
        dontRunEventLoopThread: Boolean = false
    ) : this(createProxy(destination, objectPath, dontRunEventLoopThread))

    constructor(
        connection: com.monkopedia.sdbus.IConnection,
        destination: ServiceName,
        objectPath: ObjectPath
    ) : this(createProxy(connection, destination, objectPath))

    var m_gotSimpleSignal = atomic(false)
    var m_gotSignalWithMap = atomic(false)
    var m_mapFromSignal = emptyMap<Int, String>()
    var m_gotSignalWithVariant = atomic(false)
    var m_variantFromSignal = 0.0

    var m_DoOperationClientSideAsyncReplyHandler: ((UInt, Error?) -> Unit)? = null
    var m_onPropertiesChangedHandler: (
        (InterfaceName, Map<PropertyName, Variant>, List<PropertyName>) -> Unit
    )? =
        null

    var m_signalMsg: Message? = null
    var m_signalName: SignalName? = null

    override fun onSimpleSignal() {
        m_signalMsg = proxy.getCurrentlyProcessedMessage()
        m_signalName = m_signalMsg!!.getMemberName()?.let(::SignalName)

        m_gotSimpleSignal.value = true
    }

    override fun onSignalWithMap(aMap: Map<Int, String>) {
        m_mapFromSignal = aMap
        m_gotSignalWithMap.value = true
    }

    override fun onSignalWithVariant(aVariant: Variant) {
        m_variantFromSignal = aVariant.get<Double>()
        m_gotSignalWithVariant.value = true
    }

    fun onDoOperationReply(returnValue: UInt, error: Error?) {
        m_DoOperationClientSideAsyncReplyHandler?.invoke(returnValue, error)
    }

    override fun onPropertiesChanged(
        interfaceName: InterfaceName,
        changedProperties: Map<PropertyName, Variant>,
        invalidatedProperties: List<PropertyName>
    ) {
        m_onPropertiesChangedHandler?.invoke(
            interfaceName,
            changedProperties,
            invalidatedProperties
        )
    }

    fun installDoOperationClientSideAsyncReplyHandler(handler: (UInt, Error?) -> Unit) {
        m_DoOperationClientSideAsyncReplyHandler = handler
    }

    fun doOperationWithTimeout(timeout: Duration, param: UInt): UInt =
        proxy.callMethod(INTERFACE_NAME, "doOperation") {
            timeoutDuration = timeout
            call(param)
        }

    fun doOperationClientSideAsync(param: UInt): Job = GlobalScope.launch {
        val result = runCatching {
            proxy.callMethodAsync<UInt>(INTERFACE_NAME, "doOperation") {
                call(param)
            }
        }
        ensureActive()
        result.onSuccess {
            onDoOperationReply(it, null)
        }.onFailure {
            onDoOperationReply(0u, it.toError())
        }
    }

    suspend fun awaitOperationClientSideAsync(param: UInt): UInt =
        proxy.callMethodAsync(INTERFACE_NAME, "doOperation") { call(param) }

    suspend fun doOperationClientSideAsyncOnBasicAPILevel(param: UInt): MethodReply {
        val methodCall = proxy.createMethodCall(
            INTERFACE_NAME,
            "doOperation"
        )
        methodCall.append(param)

        return proxy.callMethodAsync(methodCall)
    }

    fun doErroneousOperationClientSideAsync() = GlobalScope.launch {
        val result = runCatching {
            proxy.callMethodAsync<UInt>(INTERFACE_NAME, "throwError") { call() }
        }
        ensureActive()
        result.onSuccess {
            onDoOperationReply(it, null)
        }.onFailure {
            onDoOperationReply(0u, it.toError())
        }
    }

    suspend fun awaitErroneousOperationClientSideAsync(): Unit =
        proxy.callMethodAsync(INTERFACE_NAME, "throwError") {}

    fun doOperationClientSideAsyncWithTimeout(timeout: Duration, param: UInt) {
        GlobalScope.launch {
            val result = runCatching {
                proxy.callMethodAsync<UInt>(INTERFACE_NAME, "doOperation") {
                    timeoutDuration = timeout
                    call(param)
                }
            }
            ensureActive()
            result.onSuccess {
                onDoOperationReply(it, null)
            }.onFailure {
                onDoOperationReply(0u, it.toError())
            }
        }
    }

    fun callNonexistentMethod(): Int = proxy.callMethod(INTERFACE_NAME, "callNonexistentMethod") {}

    fun callMethodOnNonexistentInterface(): Int {
        val nonexistentInterfaceName = InterfaceName("sdbuscpp.interface.that.does.not.exist")
        return proxy.callMethod(nonexistentInterfaceName, "someMethod") {}
    }

    fun setStateProperty(value: String) {
        proxy.setProperty(INTERFACE_NAME, "state", value)
    }
}
