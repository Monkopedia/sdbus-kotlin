package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.ObjectImpl
import platform.posix.EINVAL

actual fun createObject(connection: Connection, objectPath: ObjectPath): Object {
    val sdbusConnection = connection as? com.monkopedia.sdbus.internal.InternalConnection
    sdbusRequire(
        sdbusConnection == null,
        "Connection is not a real sdbus-c++ connection",
        EINVAL
    )

    return ObjectImpl(sdbusConnection!!, objectPath)
}
