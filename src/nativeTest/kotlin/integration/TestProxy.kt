@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.IProxy
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodReply
import com.monkopedia.sdbus.ObjectManagerProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PendingAsyncCall
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Resource
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.callMethod
import com.monkopedia.sdbus.callMethodAsync
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.dont_run_event_loop_thread_t
import com.monkopedia.sdbus.onError
import com.monkopedia.sdbus.return_slot_t
import com.monkopedia.sdbus.setProperty
import com.monkopedia.sdbus.toError
import com.monkopedia.sdbus.with_future
import com.monkopedia.sdbus.with_future_t
import kotlin.time.Duration
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import sdbus.uint32_t

class ObjectManagerTestProxy(
    proxy: IProxy
) : ObjectManagerProxy {
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
        m_onInterfacesAddedHandler?.invoke(objectPath, interfacesAndProperties);
    }

    override fun onInterfacesRemoved(objectPath: ObjectPath, interfaces: List<InterfaceName>) {
        m_onInterfacesRemovedHandler?.invoke(objectPath, interfaces);
    }

    var m_onInterfacesAddedHandler: ((ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>) -> Unit)? =
        null
    var m_onInterfacesRemovedHandler: ((ObjectPath, List<InterfaceName>) -> Unit)? = null
};


class TestProxy private constructor(proxy: IProxy) :
    IntegrationTestsProxy(proxy) {

    constructor(destination: ServiceName, objectPath: ObjectPath) : this(
        createProxy(destination, objectPath)
    )

    constructor(
        destination: ServiceName,
        objectPath: ObjectPath,
        dont_run_event_loop_thread: dont_run_event_loop_thread_t
    ) : this(createProxy(destination, objectPath, dont_run_event_loop_thread))

    constructor(
        connection: com.monkopedia.sdbus.IConnection,
        destination: ServiceName,
        objectPath: ObjectPath
    ) : this(createProxy(connection, destination, objectPath))

    var m_gotSimpleSignal = atomic(false);
    var m_gotSignalWithMap = atomic(false);
    var m_mapFromSignal = emptyMap<Int, String>()
    var m_gotSignalWithVariant = atomic(false);
    var m_variantFromSignal = 0.0

    var m_DoOperationClientSideAsyncReplyHandler: ((UInt, Error?) -> Unit)? = null
    var m_onPropertiesChangedHandler: ((InterfaceName, Map<PropertyName, Variant>, List<PropertyName>) -> Unit)? =
        null

    var m_signalMsg: Message? = null
    var m_signalName: SignalName? = null


    override fun onSimpleSignal() {
        m_signalMsg = proxy.getCurrentlyProcessedMessage()
        m_signalName = m_signalMsg!!.getMemberName()?.let(::SignalName)

        m_gotSimpleSignal.value = true;
    }

    override fun onSignalWithMap(aMap: Map<Int, String>) {
        m_mapFromSignal = aMap;
        m_gotSignalWithMap.value = true;
    }

    override fun onSignalWithVariant(aVariant: Variant) {
        m_variantFromSignal = aVariant.get<Double>();
        m_gotSignalWithVariant.value = true;
    }

    fun onDoOperationReply(returnValue: UInt, error: Error?) {
        m_DoOperationClientSideAsyncReplyHandler?.invoke(returnValue, error);
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
        );
    }

    fun installDoOperationClientSideAsyncReplyHandler(handler: (UInt, Error?) -> Unit) {
        m_DoOperationClientSideAsyncReplyHandler = handler;
    }

    fun doOperationWithTimeout(timeout: Duration, param: UInt): UInt =
        proxy.callMethod("doOperation").onInterface(INTERFACE_NAME)
            .withTimeout(timeout).withArguments { call(param) }.readResult()

    fun doOperationClientSideAsync(param: UInt): PendingAsyncCall {
        return proxy.callMethodAsync("doOperation")
            .onInterface(INTERFACE_NAME)
            .withArguments { call(param) }
            .uponReplyInvoke {
                call { returnValue: UInt ->
                    onDoOperationReply(returnValue, null);
                } onError { error ->
                    onDoOperationReply(0u, (error ?: Throwable()).toError())
                }
            }
    }

    fun doOperationClientSideAsync(param: UInt, return_slot: return_slot_t): Resource {
        return proxy.callMethodAsync("doOperation")
            .onInterface(INTERFACE_NAME)
            .withArguments { call(param) }
            .uponReplyInvoke(
                {
                    call { returnValue: UInt ->
                        onDoOperationReply(returnValue, null);
                    } onError { error ->
                        onDoOperationReply(0u, (error ?: Throwable()).toError())
                    }

                }, return_slot
            );
    }

    suspend fun doOperationClientSideAsync(param: uint32_t, with_future: with_future_t): UInt =
        proxy.callMethodAsync("doOperation")
            .onInterface(INTERFACE_NAME)
            .withArguments { call(param) }
            .getResult<uint32_t>();

    suspend fun doOperationClientSideAsyncOnBasicAPILevel(param: UInt): MethodReply {
        val methodCall = proxy.createMethodCall(
            INTERFACE_NAME,
            "doOperation"
        )
        methodCall.append(param);

        return proxy.callMethodAsync(methodCall, with_future)
    }

    fun doErroneousOperationClientSideAsync() {
        proxy.callMethodAsync("throwError")
            .onInterface(INTERFACE_NAME)
            .uponReplyInvoke {
                call {
                    ->
                    onDoOperationReply(0u, null);
                } onError { error ->
                    onDoOperationReply(0u, (error ?: Throwable()).toError());
                }
            }
    }

    suspend fun doErroneousOperationClientSideAsync(with_future: with_future_t): Unit {
        proxy.callMethodAsync("throwError")
            .onInterface(INTERFACE_NAME)
            .getResult<Unit>();
    }

    fun doOperationClientSideAsyncWithTimeout(timeout: Duration, param: UInt) {
        proxy.callMethodAsync("doOperation")
            .onInterface(INTERFACE_NAME)
            .withTimeout(timeout)
            .withArguments { call(param) }
            .uponReplyInvoke {
                call { returnValue: UInt ->
                    onDoOperationReply(returnValue, null);
                } onError { error ->
                    onDoOperationReply(0u, (error ?: Throwable()).toError());
                }
            };
    }

    fun callNonexistentMethod(): Int {
        return proxy.callMethod("callNonexistentMethod").onInterface(INTERFACE_NAME)
            .readResult();
    }

    fun callMethodOnNonexistentInterface(): Int {
        val nonexistentInterfaceName = InterfaceName("sdbuscpp.interface.that.does.not.exist");
        return proxy.callMethod("someMethod").onInterface(nonexistentInterfaceName)
            .readResult()
    }

    fun setStateProperty(value: String) {
        proxy.setProperty("state").onInterface(INTERFACE_NAME).toValue(value);
    }

}

