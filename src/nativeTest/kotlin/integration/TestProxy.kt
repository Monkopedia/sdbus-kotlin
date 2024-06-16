@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.Connection
import com.monkopedia.sdbus.header.Error
import com.monkopedia.sdbus.header.IConnection
import com.monkopedia.sdbus.header.IProxy
import com.monkopedia.sdbus.header.InterfaceName
import com.monkopedia.sdbus.header.Message
import com.monkopedia.sdbus.header.MethodReply
import com.monkopedia.sdbus.header.ObjectManagerProxy
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.PendingAsyncCall
import com.monkopedia.sdbus.header.PropertyName
import com.monkopedia.sdbus.header.Proxy
import com.monkopedia.sdbus.header.ServiceName
import com.monkopedia.sdbus.header.SignalName
import com.monkopedia.sdbus.header.Signature
import com.monkopedia.sdbus.header.Variant
import com.monkopedia.sdbus.header.callMethod
import com.monkopedia.sdbus.header.callMethodAsync
import com.monkopedia.sdbus.header.createProxy
import com.monkopedia.sdbus.header.dont_run_event_loop_thread_t
import com.monkopedia.sdbus.header.onError
import com.monkopedia.sdbus.header.return_slot_t
import com.monkopedia.sdbus.header.setProperty
import com.monkopedia.sdbus.header.toError
import com.monkopedia.sdbus.header.with_future
import com.monkopedia.sdbus.header.with_future_t
import com.monkopedia.sdbus.internal.Scope
import com.monkopedia.sdbus.internal.Slot
import com.monkopedia.sdbus.internal.Unowned
import kotlin.time.Duration
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.DeferScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import sdbus.uint32_t

class ObjectManagerTestProxy(
    initialScope: DeferScope,
    proxy: Unowned<Proxy>
) : Scope(initialScope), ObjectManagerProxy {
    override val m_proxy: IProxy = proxy.own(scope)

    constructor(
        scope: DeferScope,
        connection: IConnection,
        destination: ServiceName,
        objectPath: ObjectPath
    )
        : this(scope, createProxy(connection, destination, objectPath))

    init {
        registerObjectManagerProxy()
    }

    override fun onScopeCleared() {
        super.onScopeCleared()
        m_proxy.unregister()
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


class TestProxy private constructor(initialScope: DeferScope, proxy: Unowned<Proxy>) :
    IntegrationTestsProxy(initialScope, proxy) {

    constructor(initialScope: DeferScope, destination: ServiceName, objectPath: ObjectPath) : this(
        initialScope,
        createProxy(destination, objectPath)
    )

    constructor(
        initialScope: DeferScope,
        destination: ServiceName,
        objectPath: ObjectPath,
        dont_run_event_loop_thread: dont_run_event_loop_thread_t
    ) : this(initialScope, createProxy(destination, objectPath, dont_run_event_loop_thread))

    constructor(
        initialScope: DeferScope,
        connection: Connection,
        destination: ServiceName,
        objectPath: ObjectPath
    ) : this(initialScope, createProxy(connection, destination, objectPath))

    var m_SimpleSignals = 0;
    var m_gotSimpleSignal = atomic(false);
    var m_gotSignalWithMap = atomic(false);
    var m_mapFromSignal = emptyMap<Int, String>()
    var m_gotSignalWithVariant = atomic(false);
    var m_variantFromSignal = 0.0
    var m_gotSignalWithSignature = atomic(false);
    var m_signatureFromSignal = emptyMap<String, Signature>()

    var m_DoOperationClientSideAsyncReplyHandler: ((UInt, Error?) -> Unit)? = null
    var m_onPropertiesChangedHandler: ((InterfaceName, Map<PropertyName, Variant>, List<PropertyName>) -> Unit)? =
        null

    var m_signalMsg: Message? = null
    var m_signalName: SignalName? = null


    override fun onSimpleSignal() {
        m_signalMsg = getProxy().getCurrentlyProcessedMessage()
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

//    void onSignalWithoutRegistration(const sdbus::Struct<std::string, sdbus::Struct<sdbus::Signature>>& s)
//    {
//        // Static cast to std::string is a workaround for gcc 11.4 false positive warning (which later gcc versions nor Clang emit)
//        m_signatureFromSignal[std::get<0>(s)] = static_cast<std::string>(std::get<0>(std::get<1>(s)));
//        m_gotSignalWithSignature = true;
//    }

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

    fun doOperationWithTimeout(timeout: Duration, param: UInt): UInt = memScoped {
        getProxy().callMethod("doOperation").own(this).onInterface(INTERFACE_NAME)
            .withTimeout(timeout).withArguments { call(param) }.readResult()
    }

    fun doOperationClientSideAsync(param: UInt): PendingAsyncCall {
        return getProxy().callMethodAsync("doOperation")
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

    fun doOperationClientSideAsync(param: UInt, return_slot: return_slot_t): Unowned<Slot> {
        return getProxy().callMethodAsync("doOperation")
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
            getProxy().callMethodAsync("doOperation")
                .onInterface(INTERFACE_NAME)
                .withArguments { call(param) }
                .getResult<uint32_t>();

    suspend fun doOperationClientSideAsyncOnBasicAPILevel(param: UInt): MethodReply {
        val methodCall = getProxy().createMethodCall(
            INTERFACE_NAME,
            "doOperation"
        )
        methodCall.append(param);

        return getProxy().callMethodAsync(methodCall, with_future)
    }

    fun doErroneousOperationClientSideAsync() {
        getProxy().callMethodAsync("throwError")
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

    suspend fun doErroneousOperationClientSideAsync(with_future: with_future_t): Unit = memScoped {
        getProxy().callMethodAsync("throwError")
            .onInterface(INTERFACE_NAME)
            .getResult<Unit>();
    }

    fun doOperationClientSideAsyncWithTimeout(timeout: Duration, param: UInt) {
        getProxy().callMethodAsync("doOperation")
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

    fun callNonexistentMethod(): Int = memScoped {
        return getProxy().callMethod("callNonexistentMethod").own(this).onInterface(INTERFACE_NAME)
            .readResult();
    }

    fun callMethodOnNonexistentInterface(): Int = memScoped {
        val nonexistentInterfaceName = InterfaceName("sdbuscpp.interface.that.does.not.exist");
        return getProxy().callMethod("someMethod").own(this).onInterface(nonexistentInterfaceName)
            .readResult()
    }

    fun setStateProperty(value: String) = memScoped {
        getProxy().setProperty("state").onInterface(INTERFACE_NAME).toValue(value);
    }

}

class DummyProxy private constructor(initialScope: DeferScope, proxy: Unowned<Proxy>) :
    IntegrationTestsProxy(initialScope, proxy) {

    constructor(initialScope: DeferScope, destination: ServiceName, objectPath: ObjectPath) : this(
        initialScope,
        createProxy(destination, objectPath)
    )

    override fun onSimpleSignal() {
    }

    override fun onSignalWithMap(aMap: Map<Int, String>) {
    }

    override fun onSignalWithVariant(aVariant: Variant) {
    }


}
