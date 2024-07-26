package com.monkopedia.sdbus

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/*!
 * @brief Gets value of a property of the D-Bus object
 *
 * @param[in] propertyName Name of the property
 * @return A helper object for convenient getting of property value
 *
 * This is a high-level, convenience way of reading D-Bus property values that abstracts
 * from the D-Bus message concept. sdbus::Variant is returned which shall then be converted
 * to the real property type (implicit conversion is supported).
 *
 * Example of use:
 * @code
 * int state = object.getProperty("state").onInterface("com.kistler.foo");
 * sdbus::InterfaceName foo{"com.kistler.foo"};
 * sdbus::PropertyName level{"level"};
 * int level = object.getProperty(level).onInterface(foo);
 * @endcode
 *
 * @throws sdbus::Error in case of failure
 */
inline fun <reified T : Any> IProxy.getProperty(
    interfaceName: InterfaceName,
    propertyName: PropertyName
): T = getProperty(interfaceName.value, propertyName.value)

inline fun <reified T : Any> IProxy.getProperty(
    interfaceName: InterfaceName,
    propertyName: String
): T = getProperty(interfaceName.value, propertyName)

/*!
 * @copydoc IProxy::getProperty(const PropertyName&)
 */
inline fun <reified T : Any> IProxy.getProperty(interfaceName: String, propertyName: String): T =
    callMethod<Variant>(DBUS_PROPERTIES_INTERFACE_NAME, "Get") {
        call(interfaceName, propertyName)
    }.get<T>()

inline fun <reified T : Any> IProxy.setProperty(
    interfaceName: InterfaceName,
    propertyName: PropertyName,
    value: T
): Unit = setProperty(interfaceName.value, propertyName.value, value)

inline fun <reified T : Any> IProxy.setProperty(
    interfaceName: InterfaceName,
    propertyName: String,
    value: T
): Unit = setProperty(interfaceName.value, propertyName, value)

inline fun <reified T : Any> IProxy.setProperty(
    interfaceName: String,
    propertyName: String,
    value: T,
    dontExpectReply: Boolean = false
) {
    callMethod<Unit>(DBUS_PROPERTIES_INTERFACE_NAME, "Set") {
        this.dontExpectReply = dontExpectReply
        call(interfaceName, propertyName, Variant(value))
    }
}

inline fun <R, reified T : Any> IProxy.prop(
    interfaceName: InterfaceName,
    propertyName: PropertyName
): ReadWriteProperty<R, T> = prop(interfaceName.value, propertyName.value)

inline fun <R, reified T : Any> IProxy.prop(
    interfaceName: InterfaceName,
    propertyName: String
): ReadWriteProperty<R, T> = prop(interfaceName.value, propertyName)

inline fun <R, reified T : Any> IProxy.prop(
    interfaceName: String,
    propertyName: String
): ReadWriteProperty<R, T> {
    return object : ReadWriteProperty<R, T> {
        override fun getValue(thisRef: R, property: KProperty<*>): T {
            return getProperty(interfaceName, propertyName)
        }

        override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
            setProperty(interfaceName, propertyName, value)
        }
    }
}

const val DBUS_PROPERTIES_INTERFACE_NAME = "org.freedesktop.DBus.Properties"
