
package com.monkopedia.sdbus

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

class AsyncPropertyGetter(private val proxy: Proxy, private val propertyName: PropertyName) {

    private var interfaceName: String? = null

    fun onInterface(interfaceName: InterfaceName): AsyncPropertyGetter =
        onInterface(interfaceName.value)

    fun onInterface(interfaceName: String): AsyncPropertyGetter = apply {
        this.interfaceName = interfaceName
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
        require(interfaceName?.isNotEmpty() == true)
        return proxy.callMethodAsync<Variant>(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("Get")) {
            call(interfaceName!!, propertyName)
        }.get(serializer, module, signature)
    }

    companion object {
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}

class AsyncPropertySetter(private val proxy: Proxy, private val propertyName: PropertyName) {

    private var value: Pair<Typed<*>, Any>? = null
    private var interfaceName: InterfaceName? = null

    fun onInterface(interfaceName: InterfaceName): AsyncPropertySetter = apply {
        this.interfaceName = interfaceName
    }

    inline fun <reified T : Any> toValue(value: T) = toValue(typed<T>(), value)

    @PublishedApi
    internal fun <T : Any> toValue(type: Typed<T>, value: T): AsyncPropertySetter = apply {
        this.value = type to value
    }

    suspend fun getResult() {
        require(interfaceName?.value?.isNotEmpty() == true)

        return proxy.callMethodAsync<Unit>(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("Set")) {
            args = call(interfaceName!!, propertyName) + (value!!)
        }
    }

    companion object {
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}

class AllPropertiesGetter(val proxy: Proxy) {
    fun onInterface(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        proxy.callMethod(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("GetAll")) {
            call(interfaceName)
        }

    companion object {
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}

class AsyncAllPropertiesGetter(private val proxy: Proxy) {
    private var interfaceName: InterfaceName? = null

    fun onInterface(interfaceName: InterfaceName): AsyncAllPropertiesGetter = apply {
        this.interfaceName = interfaceName
    }

    suspend fun getResult(): Map<PropertyName, Variant> {
        require(interfaceName?.value?.isNotEmpty() == true)
        return proxy.callMethodAsync(DBUS_PROPERTIES_INTERFACE_NAME, MethodName("GetAll")) {
            call(interfaceName!!)
        }
    }

    companion object {
        val DBUS_PROPERTIES_INTERFACE_NAME = InterfaceName("org.freedesktop.DBus.Properties")
    }
}
