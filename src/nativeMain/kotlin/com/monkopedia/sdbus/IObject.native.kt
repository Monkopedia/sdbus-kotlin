package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.Object
import platform.posix.EINVAL

actual fun createObject(connection: IConnection, objectPath: ObjectPath): IObject {
    val sdbusConnection = connection as? com.monkopedia.sdbus.internal.IConnection
    sdbusRequire(
        sdbusConnection == null,
        "Connection is not a real sdbus-c++ connection",
        EINVAL
    )

    return Object(sdbusConnection!!, objectPath)
}
