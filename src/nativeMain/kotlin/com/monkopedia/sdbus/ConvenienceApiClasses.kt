@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

class PropertyGetter(private val proxy: IProxy, private val propertyName: String) {

    inline fun <reified T : Any> onInterface(interfaceName: InterfaceName): T {
        val serializer = serializer<T>()
        val module = serializersModuleOf(serializer)
        return onInterface(
            interfaceName.value,
            serializer,
            module,
            signatureOf<T>()
        )
    }

    inline fun <reified T : Any> onInterface(interfaceName: String): T {
        val serializer = serializer<T>()
        val module = serializersModuleOf(serializer)
        return onInterface(
            interfaceName,
            serializer,
            module,
            signatureOf<T>()
        )
    }

    fun <T : Any> onInterface(
        interfaceName: String,
        serializer: DeserializationStrategy<T>,
        module: SerializersModule,
        signature: SdbusSig
    ): T = proxy.callMethod<Variant>(DBUS_PROPERTIES_INTERFACE_NAME, "Get") {
        call(interfaceName, propertyName)
    }.get(serializer, module, signature)

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

class AsyncPropertyGetter(private val proxy: IProxy, private val propertyName: String) {

    private var interfaceName_: String? = null

    fun onInterface(interfaceName: InterfaceName): AsyncPropertyGetter =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): AsyncPropertyGetter = apply {
        interfaceName_ = interfaceName
    }

    suspend inline fun <reified T : Any> get(): T {
        val serializer = serializer<T>()
        val module = serializersModuleOf(serializer)
        return get(serializer, module, signatureOf<T>())
    }

    suspend fun <T : Any> get(
        serializer: DeserializationStrategy<T>,
        module: SerializersModule,
        signature: SdbusSig
    ): T {
        require(interfaceName_?.isNotEmpty() == true)
        return proxy.callMethodAsync<Variant>(DBUS_PROPERTIES_INTERFACE_NAME, "Get") {
            call(interfaceName_!!, propertyName)
        }.get(serializer, module, signature)
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

class PropertySetter(private val proxy: IProxy, private val propertyName: String) {
    private var interfaceName_: String? = null

    fun onInterface(interfaceName: InterfaceName): PropertySetter = onInterface(interfaceName.value)
    fun onInterface(interfaceName: String): PropertySetter = apply {
        interfaceName_ = interfaceName
    }

    inline fun <reified T : Any> toValue(value: T, dontExpectReply: Boolean = false) {
        toValue(Variant<T>(value), dontExpectReply = dontExpectReply)
    }

    fun toValue(variant: Variant, dontExpectReply: Boolean = false) {
        require(interfaceName_?.isNotEmpty() == true)

        proxy.callMethod<Unit>(DBUS_PROPERTIES_INTERFACE_NAME, "Set") {
            this.dontExpectReply = dontExpectReply
            call(interfaceName_!!, propertyName, variant)
        }
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

class AsyncPropertySetter(private val proxy: IProxy, private val propertyName: String) {

    private var value_: Pair<Typed<*>, Any>? = null
    private var interfaceName_: String? = null

    fun onInterface(interfaceName: InterfaceName): AsyncPropertySetter =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): AsyncPropertySetter = apply {
        interfaceName_ = interfaceName
    }

    inline fun <reified T : Any> toValue(value: T) = toValue(typed<T>(), value)

    fun <T : Any> toValue(type: Typed<T>, value: T): AsyncPropertySetter = apply {
        value_ = type to value
    }

    suspend fun getResult() {
        require(interfaceName_?.isNotEmpty() == true)

        return proxy.callMethodAsync<Unit>(DBUS_PROPERTIES_INTERFACE_NAME, "Set") {
            args = call(interfaceName_!!, propertyName) + (value_!!)
        }
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

class AllPropertiesGetter(val proxy: IProxy) {
    fun onInterface(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): Map<PropertyName, Variant> =
        proxy.callMethod(DBUS_PROPERTIES_INTERFACE_NAME, "GetAll") {
            call(interfaceName)
        }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

class AsyncAllPropertiesGetter(private val proxy: IProxy) {
    private var interfaceName: String? = null

    fun onInterface(interfaceName: InterfaceName): AsyncAllPropertiesGetter =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): AsyncAllPropertiesGetter = apply {
        this.interfaceName = interfaceName
    }

    suspend fun getResult(): Map<PropertyName, Variant> {
        require(interfaceName?.isNotEmpty() == true)
        return proxy.callMethodAsync(DBUS_PROPERTIES_INTERFACE_NAME, "GetAll") {
            call(interfaceName!!)
        }
    }

    companion object {
        const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}
