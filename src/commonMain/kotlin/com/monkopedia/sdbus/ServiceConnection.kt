package com.monkopedia.sdbus

fun Connection.withService(serviceName: ServiceName): ServiceConnection =
    ServiceConnection(this, serviceName)

/**
 * A Simple wrapper around a connection that avoids repeating the service name when creating
 * proxies.
 */
class ServiceConnection(val connection: Connection, val serviceName: ServiceName) {

    fun createProxy(objectPath: ObjectPath): Proxy =
        createProxy(connection, serviceName, objectPath)

    fun createObject(objectPath: ObjectPath): Object = createObject(connection, objectPath)
}
