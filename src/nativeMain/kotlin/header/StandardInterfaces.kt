@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.header

import com.monkopedia.sdbus.internal.Slot
import kotlinx.cinterop.DeferScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped

interface ProxyHolder {
    val m_proxy: IProxy
}

interface PeerProxy : ProxyHolder {
    fun Ping() {
        memScoped {
            m_proxy.callMethod("Ping").onInterface(INTERFACE_NAME);
        }
    }

    fun GetMachineId(): String {
        memScoped {
            return m_proxy.callMethod("GetMachineId")
                .onInterface(INTERFACE_NAME)
                .readResult<String>()
        }
    }

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.Peer";
    }
};

// Proxy for introspection
interface IntrospectableProxy : ProxyHolder {

    fun Introspect(): String {
        memScoped {
            return m_proxy.callMethod("Introspect")
                .onInterface(INTERFACE_NAME)
                .readResult<String>()
        }
    }

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.Introspectable";
    }
};

// Proxy for properties
interface PropertiesProxy : ProxyHolder {

    fun registerPropertiesProxy() {
        m_proxy
            .uponSignal("PropertiesChanged")
            .onInterface(INTERFACE_NAME)
            .call {
                call { interfaceName: InterfaceName, changedProperties: Map<PropertyName, Variant>, invalidatedProperties: List<PropertyName> ->

                    this@PropertiesProxy.onPropertiesChanged(
                        interfaceName,
                        changedProperties,
                        invalidatedProperties
                    );
                }
            };
    }

    fun onPropertiesChanged(
        interfaceName: InterfaceName,
        changedProperties: Map<PropertyName, Variant>,
        invalidatedProperties: List<PropertyName>
    ) = Unit

    fun Set(interfaceName: InterfaceName, propertyName: PropertyName, value: Variant) {
        m_proxy.setProperty(propertyName).onInterface(interfaceName).toValue(value);
    }

    fun Set(interfaceName: String, propertyName: String, value: Variant) {
        m_proxy.setProperty(propertyName).onInterface(interfaceName).toValue(value);
    }

    fun Set(
        interfaceName: InterfaceName,
        propertyName: PropertyName,
        value: Variant,
        dont_expect_reply: dont_expect_reply_t
    ) {
        m_proxy.setProperty(propertyName).onInterface(interfaceName)
            .toValue(value, dont_expect_reply);
    }

    fun Set(
        interfaceName: String,
        propertyName: String,
        value: Variant,
        dont_expect_reply: dont_expect_reply_t
    ) {
        m_proxy.setProperty(propertyName).onInterface(interfaceName)
            .toValue(value, dont_expect_reply);
    }

    fun SetAsync(
        interfaceName: InterfaceName,
        propertyName: PropertyName,
        value: Variant,
        callback: TypedMethodCall<*>
    ): PendingAsyncCall {
        return m_proxy.setPropertyAsync(propertyName).onInterface(interfaceName).toValue(value)
            .uponReplyInvoke(callback);
    }

    fun SetAsync(
        interfaceName: InterfaceName,
        propertyName: PropertyName,
        value: Variant,
        callback: TypedMethodCall<*>,
        return_slot: return_slot_t
    ): Slot {
        return m_proxy.setPropertyAsync(propertyName).onInterface(interfaceName).toValue(value)
            .uponReplyInvoke(callback, return_slot);
    }

    suspend fun SetAsync(
        interfaceName: InterfaceName,
        propertyName: PropertyName,
        value: Variant
    ) {
        return m_proxy.setPropertyAsync(propertyName).onInterface(interfaceName).toValue(value)
            .getResult();
    }

    suspend fun SetAsync(
        interfaceName: String,
        propertyName: String,
        value: Variant
    ) {
        return m_proxy.setPropertyAsync(propertyName).onInterface(interfaceName).toValue(value)
            .getResult();
    }

    fun GetAll(interfaceName: InterfaceName, scope: DeferScope? = null): Map<PropertyName, Variant> {
        return m_proxy.getAllProperties().onInterface(interfaceName);
    }

    fun GetAll(interfaceName: String, scope: DeferScope? = null): Map<PropertyName, Variant> {
        return m_proxy.getAllProperties().onInterface(interfaceName);
    }

    suspend fun GetAllAsync(
        interfaceName: InterfaceName,
        scope: DeferScope? = null
    ): Map<PropertyName, Variant> {
        return m_proxy.getAllPropertiesAsync().onInterface(interfaceName).getResult();
    }

    suspend fun GetAllAsync(
        interfaceName: String,
        scope: DeferScope? = null
    ): Map<PropertyName, Variant> {
        return m_proxy.getAllPropertiesAsync().onInterface(interfaceName).getResult();
    }

    companion object {



        suspend inline fun <reified T> PropertiesProxy.GetAsync(
            interfaceName: InterfaceName,
            propertyName: PropertyName,
        ): T {
            return m_proxy.getPropertyAsync(propertyName).onInterface(interfaceName).get();
        }

        suspend inline fun <reified T> PropertiesProxy.GetAsync(
            interfaceName: String,
            propertyName: String,
        ): T {
            return m_proxy.getPropertyAsync(propertyName).onInterface(interfaceName).get();
        }

        inline fun <reified T> PropertiesProxy.Get(interfaceName: InterfaceName, propertyName: PropertyName): T {
            return m_proxy.getProperty(propertyName).onInterface(interfaceName)
        }

        inline fun <reified T>  PropertiesProxy.Get(interfaceName: String, propertyName: String): T {
            return m_proxy.getProperty(propertyName).onInterface(interfaceName);
        }
        const val INTERFACE_NAME = "org.freedesktop.DBus.Properties";
    }
}

// Proxy for object manager
interface ObjectManagerProxy : ProxyHolder {

    fun registerObjectManagerProxy() {
        m_proxy
            .uponSignal("InterfacesAdded")
            .onInterface(INTERFACE_NAME)
            .call {
                call { objectPath: ObjectPath,
                       interfacesAndProperties: Map<InterfaceName, Map<PropertyName, Variant>> ->
                    this@ObjectManagerProxy.onInterfacesAdded(objectPath, interfacesAndProperties);
                }
            }
        m_proxy
            .uponSignal("InterfacesRemoved")
            .onInterface(INTERFACE_NAME)
            .call {
                call { objectPath: ObjectPath,
                       interfaces: List<InterfaceName> ->
                    this@ObjectManagerProxy.onInterfacesRemoved(objectPath, interfaces);
                }
            };
    }

    fun onInterfacesAdded(
        objectPath: ObjectPath,
        interfacesAndProperties: Map<InterfaceName, Map<PropertyName, Variant>>
    )

    fun onInterfacesRemoved(objectPath: ObjectPath, interfaces: List<InterfaceName>)

    fun getManagedObjects(): Map<ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>> {
        memScoped {
            return m_proxy.callMethod("GetManagedObjects")
                .onInterface(INTERFACE_NAME)
                .readResult()
        }
    }


    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.ObjectManager";
    }
};

interface ObjectAdaptor {
    val m_object: IObject
}

// Adaptors for the above-listed standard D-Bus interfaces are not necessary because the functionality
// is provided by underlying libsystemd implementation. The exception is Properties_adaptor,
// ObjectManager_adaptor and ManagedObject_adaptor, which provide convenience functionality to emit signals.

// Adaptor for properties
interface PropertiesAdaptor : ObjectAdaptor {

    fun emitPropertiesChangedSignal(interfaceName: InterfaceName, properties: List<PropertyName>) {
        m_object.emitPropertiesChangedSignal(interfaceName, properties);
    }

    fun emitPropertiesChangedSignal(interfaceName: String, properties: List<PropertyName>) {
        m_object.emitPropertiesChangedSignal(interfaceName, properties);
    }

    fun emitPropertiesChangedSignal(interfaceName: InterfaceName) {
        m_object.emitPropertiesChangedSignal(interfaceName);
    }

    fun emitPropertiesChangedSignal(interfaceName: String) {
        m_object.emitPropertiesChangedSignal(interfaceName);
    }

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.Properties";
    }
};

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
        m_object.addObjectManager();
    }

    companion object {
        const val INTERFACE_NAME = "org.freedesktop.DBus.ObjectManager";
    }
};

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
        m_object.emitInterfacesAddedSignal();
    }

    /*!
     * @brief Emits InterfacesAdded signal for this object path
     *
     * See IObject::emitInterfacesAddedSignal().
     */
    fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>) {
        m_object.emitInterfacesAddedSignal(interfaces);
    }

    /*!
     * @brief Emits InterfacesRemoved signal for this object path
     *
     * See IObject::emitInterfacesRemovedSignal().
     */
    fun emitInterfacesRemovedSignal() {
        m_object.emitInterfacesRemovedSignal();
    }

    /*!
     * @brief Emits InterfacesRemoved signal for this object path
     *
     * See IObject::emitInterfacesRemovedSignal().
     */
    fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>) {
        m_object.emitInterfacesRemovedSignal(interfaces);
    }

};
