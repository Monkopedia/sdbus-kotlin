package com.monkopedia.sdbus

/**
 * Convenient way to unify setup of destination.
 *
 * @See [ServiceConnection.createProxy]
 */
fun Connection.withService(serviceName: ServiceName): ServiceConnection =
    ServiceConnection(this, serviceName)

/**
 * A Simple wrapper around a connection that avoids repeating the service name when creating
 * proxies.
 */
class ServiceConnection(val connection: Connection, val serviceName: ServiceName) {

    /**
     * Allows simply creation of a proxy without specifying a serviceName.
     *
     * Example usage:
     * ```
     * val service = createSystemBusConnection().withService(ServiceName("com.monkopedia.foo"))
     * val proxy1 = service.createProxy(ObjectPath("/objs/first"))
     * val proxy2 = service.createProxy(ObjectPath("/objs/second"))
     * ```
     */
    fun createProxy(objectPath: ObjectPath): Proxy =
        createProxy(connection, serviceName, objectPath)

    /**
     * API available as parity to [createProxy] method.
     *
     * Creates an object on the given connection.
     */
    fun createObject(objectPath: ObjectPath): Object = createObject(connection, objectPath)
}
