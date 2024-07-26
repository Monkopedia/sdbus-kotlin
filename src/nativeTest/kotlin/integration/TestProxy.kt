/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sdbus-kotlin. If not, see <http://www.gnu.org/licenses/>.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.MethodReply
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Proxy
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

class TestProxy private constructor(proxy: Proxy) : IntegrationTestsProxy(proxy) {

    constructor(destination: ServiceName, objectPath: ObjectPath) : this(
        createProxy(destination, objectPath)
    )

    constructor(
        destination: ServiceName,
        objectPath: ObjectPath,
        dontRunEventLoopThread: Boolean = false
    ) : this(createProxy(destination, objectPath, dontRunEventLoopThread))

    constructor(
        connection: com.monkopedia.sdbus.Connection,
        destination: ServiceName,
        objectPath: ObjectPath
    ) : this(createProxy(connection, destination, objectPath))

    var gotSimpleSignal = atomic(false)
    var gotSignalWithMap = atomic(false)
    var mapFromSignal = emptyMap<Int, String>()
    var gotSignalWithVariant = atomic(false)
    var variantFromSignal = 0.0

    var doOperationClientSideAsyncReplyHandler: ((UInt, Error?) -> Unit)? = null
    var propertiesChangedHandler: (
        (InterfaceName, Map<PropertyName, Variant>, List<PropertyName>) -> Unit
    )? =
        null

    var signalMsg: Message? = null
    var signalName: SignalName? = null

    override fun onSimpleSignal() {
        signalMsg = proxy.currentlyProcessedMessage
        signalName = signalMsg!!.getMemberName()?.let(::SignalName)

        gotSimpleSignal.value = true
    }

    override fun onSignalWithMap(aMap: Map<Int, String>) {
        mapFromSignal = aMap
        gotSignalWithMap.value = true
    }

    override fun onSignalWithVariant(aVariant: Variant) {
        variantFromSignal = aVariant.get<Double>()
        gotSignalWithVariant.value = true
    }

    fun onDoOperationReply(returnValue: UInt, error: Error?) {
        doOperationClientSideAsyncReplyHandler?.invoke(returnValue, error)
    }

    override fun onPropertiesChanged(
        interfaceName: InterfaceName,
        changedProperties: Map<PropertyName, Variant>,
        invalidatedProperties: List<PropertyName>
    ) {
        propertiesChangedHandler?.invoke(
            interfaceName,
            changedProperties,
            invalidatedProperties
        )
    }

    fun installDoOperationClientSideAsyncReplyHandler(handler: (UInt, Error?) -> Unit) {
        doOperationClientSideAsyncReplyHandler = handler
    }

    fun doOperationWithTimeout(timeout: Duration, param: UInt): UInt =
        proxy.callMethod(INTERFACE_NAME, MethodName("doOperation")) {
            this.timeout = timeout
            call(param)
        }

    fun doOperationClientSideAsync(param: UInt): Job = GlobalScope.launch {
        val result = runCatching {
            proxy.callMethodAsync<UInt>(INTERFACE_NAME, MethodName("doOperation")) {
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
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("doOperation")) { call(param) }

    suspend fun doOperationClientSideAsyncOnBasicAPILevel(param: UInt): MethodReply {
        val methodCall = proxy.createMethodCall(
            INTERFACE_NAME,
            MethodName("doOperation")
        )
        methodCall.append(param)

        return proxy.callMethodAsync(methodCall)
    }

    fun doErroneousOperationClientSideAsync() = GlobalScope.launch {
        val result = runCatching {
            proxy.callMethodAsync<UInt>(INTERFACE_NAME, MethodName("throwError")) { call() }
        }
        ensureActive()
        result.onSuccess {
            onDoOperationReply(it, null)
        }.onFailure {
            onDoOperationReply(0u, it.toError())
        }
    }

    suspend fun awaitErroneousOperationClientSideAsync(): Unit =
        proxy.callMethodAsync(INTERFACE_NAME, MethodName("throwError")) {}

    fun doOperationClientSideAsyncWithTimeout(timeout: Duration, param: UInt) {
        GlobalScope.launch {
            val result = runCatching {
                proxy.callMethodAsync<UInt>(INTERFACE_NAME, MethodName("doOperation")) {
                    this.timeout = timeout
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

    fun callNonexistentMethod(): Int =
        proxy.callMethod(INTERFACE_NAME, MethodName("callNonexistentMethod")) {}

    fun callMethodOnNonexistentInterface(): Int {
        val nonexistentInterfaceName = InterfaceName("sdbuscpp.interface.that.does.not.exist")
        return proxy.callMethod(nonexistentInterfaceName, MethodName("someMethod")) {}
    }

    fun setStateProperty(value: String) {
        proxy.setProperty(INTERFACE_NAME, PropertyName("state"), value)
    }
}
