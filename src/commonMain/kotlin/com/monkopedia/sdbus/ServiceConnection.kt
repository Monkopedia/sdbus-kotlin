package com.monkopedia.sdbus

fun IConnection.withService(serviceName: ServiceName): ServiceConnection =
    ServiceConnection(this, serviceName)

class ServiceConnection(val connection: IConnection, val serviceName: ServiceName) {

    fun createProxy(objectPath: ObjectPath): IProxy =
        createProxy(connection, serviceName, objectPath)

    fun createObject(objectPath: ObjectPath): IObject = createObject(connection, objectPath)
}
