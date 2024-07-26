@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import kotlinx.cinterop.ExperimentalForeignApi

interface ProxyHolder {
    val proxy: IProxy
}

interface PeerProxy : ProxyHolder {
    fun ping(): Unit = proxy.callMethod(INTERFACE_NAME, "Ping") {}

    fun getMachineId(): String = proxy.callMethod(INTERFACE_NAME, "GetMachineId") {}

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.Peer"
    }
}

// Proxy for introspection
interface IntrospectableProxy : ProxyHolder {

    fun Introspect(): String = proxy.callMethod(INTERFACE_NAME, "Introspect") {}

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.Introspectable"
    }
}

// Proxy for properties
interface PropertiesProxy : ProxyHolder {

    fun registerPropertiesProxy() {
        proxy.onSignal(INTERFACE_NAME, "PropertiesChanged") {
            call {
                    interfaceName: InterfaceName,
                    changedProperties: Map<PropertyName, Variant>,
                    invalidatedProperties: List<PropertyName>
                ->

                onPropertiesChanged(
                    interfaceName,
                    changedProperties,
                    invalidatedProperties
                )
            }
        }
    }

    fun onPropertiesChanged(
        interfaceName: InterfaceName,
        changedProperties: Map<PropertyName, Variant>,
        invalidatedProperties: List<PropertyName>
    ) = Unit

    suspend fun setAsync(interfaceName: InterfaceName, propertyName: PropertyName, value: Variant) =
        proxy.setPropertyAsync(propertyName).onInterface(interfaceName).toValue(value)
            .getResult()

    suspend fun setAsync(interfaceName: String, propertyName: String, value: Variant) =
        proxy.setPropertyAsync(propertyName).onInterface(interfaceName).toValue(value)
            .getResult()

    fun getAll(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        proxy.getAllProperties().onInterface(interfaceName)

    fun getAll(interfaceName: String): Map<PropertyName, Variant> =
        proxy.getAllProperties().onInterface(interfaceName)

    suspend fun getAllAsync(interfaceName: InterfaceName): Map<PropertyName, Variant> =
        proxy.getAllPropertiesAsync().onInterface(interfaceName).getResult()

    suspend fun getAllAsync(interfaceName: String): Map<PropertyName, Variant> =
        proxy.getAllPropertiesAsync().onInterface(interfaceName).getResult()

    companion object {

        inline fun <reified T: Any> PropertiesProxy.set(
            interfaceName: InterfaceName,
            propertyName: PropertyName,
            value: T,
            dontExpectReply: Boolean = false
        ) {
            proxy.setProperty(
                interfaceName.value,
                propertyName.value,
                value,
                dontExpectReply = dontExpectReply
            )
        }

        inline fun <reified T: Any> PropertiesProxy.set(
            interfaceName: String,
            propertyName: String,
            value: T,
            dontExpectReply: Boolean = false
        ) {
            proxy.setProperty(interfaceName, propertyName, value, dontExpectReply = dontExpectReply)
        }

        suspend inline fun <reified T> PropertiesProxy.getAsync(
            interfaceName: InterfaceName,
            propertyName: PropertyName
        ): T = proxy.getPropertyAsync(propertyName).onInterface(interfaceName).get()

        suspend inline fun <reified T> PropertiesProxy.getAsync(
            interfaceName: String,
            propertyName: String
        ): T = proxy.getPropertyAsync(propertyName).onInterface(interfaceName).get()

        inline fun <reified T> PropertiesProxy.get(
            interfaceName: InterfaceName,
            propertyName: PropertyName
        ): T = proxy.getProperty(interfaceName, propertyName)

        inline fun <reified T> PropertiesProxy.get(interfaceName: String, propertyName: String): T =
            proxy.getProperty(interfaceName, propertyName)

        const val INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

// Proxy for object manager
interface ObjectManagerProxy : ProxyHolder {

    fun registerObjectManagerProxy() {
        proxy.onSignal(INTERFACE_NAME, "InterfacesAdded") {
            call {
                    objectPath: ObjectPath,
                    interfacesAndProperties: Map<InterfaceName, Map<PropertyName, Variant>>
                ->
                onInterfacesAdded(objectPath, interfacesAndProperties)
            }
        }
        proxy.onSignal(INTERFACE_NAME, "InterfacesRemoved") {
            call {
                    objectPath: ObjectPath,
                    interfaces: List<InterfaceName>
                ->
                onInterfacesRemoved(objectPath, interfaces)
            }
        }
    }

    fun onInterfacesAdded(
        objectPath: ObjectPath,
        interfacesAndProperties: Map<InterfaceName, Map<PropertyName, Variant>>
    )

    fun onInterfacesRemoved(objectPath: ObjectPath, interfaces: List<InterfaceName>)

    fun getManagedObjects(): Map<ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>> =
        proxy.callMethod(INTERFACE_NAME, "GetManagedObjects") {
        }

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.ObjectManager"
    }
}

interface ObjectAdaptor {
    val obj: IObject
}

// Adaptors for the above-listed standard D-Bus interfaces are not necessary because the functionality
// is provided by underlying libsystemd implementation. The exception is Properties_adaptor,
// ObjectManager_adaptor and ManagedObject_adaptor, which provide convenience functionality to emit signals.

// Adaptor for properties
interface PropertiesAdaptor : ObjectAdaptor {

    fun emitPropertiesChangedSignal(interfaceName: InterfaceName, properties: List<PropertyName>) {
        obj.emitPropertiesChangedSignal(interfaceName, properties)
    }

    fun emitPropertiesChangedSignal(interfaceName: String, properties: List<PropertyName>) {
        obj.emitPropertiesChangedSignal(interfaceName, properties)
    }

    fun emitPropertiesChangedSignal(interfaceName: InterfaceName) {
        obj.emitPropertiesChangedSignal(interfaceName)
    }

    fun emitPropertiesChangedSignal(interfaceName: String) {
        obj.emitPropertiesChangedSignal(interfaceName)
    }

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

/*!
 * @brief Object Manager Convenience Adaptor
 *
 * Adding this class as _Interfaces.. template parameter of class AdaptorInterfaces
 * implements the *GetManagedObjects()* method of the [org.freedesktop.DBus.ObjectManager.GetManagedObjects](https://dbus.freedesktop.org/doc/dbus-specification.html#standard-interfaces-objectmanager)
 * interface.
 *
 * Note that there can be multiple object managers in a path hierarchy. InterfacesAdded/InterfacesRemoved
 * signals are sent from the closest object manager at either the same path or the closest parent path of an object.
 */
interface ObjectManagerAdaptor : ObjectAdaptor {

    fun registerObjectManagerAdaptor() {
        obj.addObjectManager()
    }

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.ObjectManager"
    }
}

/*!
 * @brief Managed Object Convenience Adaptor
 *
 * Adding this class as _Interfaces.. template parameter of class AdaptorInterfaces
 * will extend the resulting object adaptor with emitInterfacesAddedSignal()/emitInterfacesRemovedSignal()
 * according to org.freedesktop.DBus.ObjectManager.InterfacesAdded/.InterfacesRemoved.
 *
 * Note that objects which implement this adaptor require an object manager (e.g via ObjectManager_adaptor) to be
 * instantiated on one of it's parent object paths or the same path. InterfacesAdded/InterfacesRemoved
 * signals are sent from the closest object manager at either the same path or the closest parent path of an object.
 */
interface ManagedObjectAdaptor : ObjectAdaptor {

    /*!
     * @brief Emits InterfacesAdded signal for this object path
     *
     * See IObject::emitInterfacesAddedSignal().
     */
    fun emitInterfacesAddedSignal() {
        obj.emitInterfacesAddedSignal()
    }

    /*!
     * @brief Emits InterfacesAdded signal for this object path
     *
     * See IObject::emitInterfacesAddedSignal().
     */
    fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>) {
        obj.emitInterfacesAddedSignal(interfaces)
    }

    /*!
     * @brief Emits InterfacesRemoved signal for this object path
     *
     * See IObject::emitInterfacesRemovedSignal().
     */
    fun emitInterfacesRemovedSignal() {
        obj.emitInterfacesRemovedSignal()
    }

    /*!
     * @brief Emits InterfacesRemoved signal for this object path
     *
     * See IObject::emitInterfacesRemovedSignal().
     */
    fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>) {
        obj.emitInterfacesRemovedSignal(interfaces)
    }
}
