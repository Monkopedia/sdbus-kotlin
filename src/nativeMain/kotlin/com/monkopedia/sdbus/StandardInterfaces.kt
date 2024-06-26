@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.Slot
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped

interface ProxyHolder {
    val proxy: IProxy
}

interface PeerProxy : ProxyHolder {
    fun ping() {
        memScoped {
            proxy.callMethod("Ping").onInterface(INTERFACE_NAME)
        }
    }

    fun getMachineId(): String {
        memScoped {
            return proxy.callMethod("GetMachineId")
                .onInterface(INTERFACE_NAME)
                .readResult<String>()
        }
    }

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.Peer"
    }
}

// Proxy for introspection
interface IntrospectableProxy : ProxyHolder {

    fun Introspect(): String {
        memScoped {
            return proxy.callMethod("Introspect")
                .onInterface(INTERFACE_NAME)
                .readResult<String>()
        }
    }

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.Introspectable"
    }
}

// Proxy for properties
interface PropertiesProxy : ProxyHolder {

    fun registerPropertiesProxy() {
        proxy
            .uponSignal("PropertiesChanged")
            .onInterface(INTERFACE_NAME)
            .call {
                call {
                        interfaceName: InterfaceName,
                        changedProperties: Map<PropertyName, Variant>,
                        invalidatedProperties: List<PropertyName>
                    ->

                    this@PropertiesProxy.onPropertiesChanged(
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

    fun set(interfaceName: InterfaceName, propertyName: PropertyName, value: Variant) {
        proxy.setProperty(propertyName).onInterface(interfaceName).toValue(value)
    }

    fun set(interfaceName: String, propertyName: String, value: Variant) {
        proxy.setProperty(propertyName).onInterface(interfaceName).toValue(value)
    }

    fun set(
        interfaceName: InterfaceName,
        propertyName: PropertyName,
        value: Variant,
        dont_expect_reply: dont_expect_reply_t
    ) {
        proxy.setProperty(propertyName).onInterface(interfaceName)
            .toValue(value, dont_expect_reply)
    }

    fun set(
        interfaceName: String,
        propertyName: String,
        value: Variant,
        dont_expect_reply: dont_expect_reply_t
    ) {
        proxy.setProperty(propertyName).onInterface(interfaceName)
            .toValue(value, dont_expect_reply)
    }

    fun setAsync(
        interfaceName: InterfaceName,
        propertyName: PropertyName,
        value: Variant,
        callback: TypedMethodCall<*>
    ): PendingAsyncCall =
        proxy.setPropertyAsync(propertyName).onInterface(interfaceName).toValue(value)
            .uponReplyInvoke(callback)

    fun setAsync(
        interfaceName: InterfaceName,
        propertyName: PropertyName,
        value: Variant,
        callback: TypedMethodCall<*>,
        return_slot: return_slot_t
    ): Slot = proxy.setPropertyAsync(propertyName).onInterface(interfaceName).toValue(value)
        .uponReplyInvoke(callback, return_slot)

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
        ): T = proxy.getProperty(propertyName).onInterface(interfaceName)

        inline fun <reified T> PropertiesProxy.get(interfaceName: String, propertyName: String): T =
            proxy.getProperty(propertyName).onInterface(interfaceName)

        const val INTERFACE_NAME = "org.freedesktop.DBus.Properties"
    }
}

// Proxy for object manager
interface ObjectManagerProxy : ProxyHolder {

    fun registerObjectManagerProxy() {
        proxy.uponSignal("InterfacesAdded")
            .onInterface(INTERFACE_NAME)
            .call {
                call {
                        objectPath: ObjectPath,
                        interfacesAndProperties: Map<InterfaceName, Map<PropertyName, Variant>>
                    ->
                    this@ObjectManagerProxy.onInterfacesAdded(objectPath, interfacesAndProperties)
                }
            }
        proxy.uponSignal("InterfacesRemoved")
            .onInterface(INTERFACE_NAME)
            .call {
                call {
                        objectPath: ObjectPath,
                        interfaces: List<InterfaceName>
                    ->
                    this@ObjectManagerProxy.onInterfacesRemoved(objectPath, interfaces)
                }
            }
    }

    fun onInterfacesAdded(
        objectPath: ObjectPath,
        interfacesAndProperties: Map<InterfaceName, Map<PropertyName, Variant>>
    )

    fun onInterfacesRemoved(objectPath: ObjectPath, interfaces: List<InterfaceName>)

    fun getManagedObjects(): Map<ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>> {
        memScoped {
            return proxy.callMethod("GetManagedObjects")
                .onInterface(INTERFACE_NAME)
                .readResult()
        }
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
