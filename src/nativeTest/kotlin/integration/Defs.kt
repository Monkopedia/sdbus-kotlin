@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.header.InterfaceName
import com.monkopedia.sdbus.header.ObjectPath
import com.monkopedia.sdbus.header.PropertyName
import com.monkopedia.sdbus.header.ServiceName
import com.monkopedia.sdbus.header.Signature
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cstr
import kotlinx.cinterop.toKString
import platform.posix.getenv
import sdbus.int16_t
import sdbus.int32_t
import sdbus.uint32_t
import sdbus.uint8_t


val INTERFACE_NAME = InterfaceName("org.sdbuscpp.integrationtests")
val SERVICE_NAME = ServiceName("org.sdbuscpp.integrationtests")
val EMPTY_DESTINATION = ServiceName("")
val MANAGER_PATH = ObjectPath("/org/sdbuscpp/integrationtests")
val OBJECT_PATH = ObjectPath("/org/sdbuscpp/integrationtests/ObjectA1")
val OBJECT_PATH_2 = ObjectPath("/org/sdbuscpp/integrationtests/ObjectB1")
val STATE_PROPERTY = PropertyName("state")
val ACTION_PROPERTY = PropertyName("action")
val BLOCKING_PROPERTY = PropertyName("blocking")
val DIRECT_CONNECTION_SOCKET_PATH =
    ((getenv("TMPDIR")?.toKString() ?: "/tmp") + "/sdbus-cpp-direct-connection-test")

val UINT8_VALUE: UByte = (1u)
val UINT16_VALUE: UShort = (21u).toUShort()
val UINT32_VALUE: UInt = (42u)
val INT16_VALUE: Short = (21).toShort()
val INT32_VALUE: Int = (-42)
val INT64_VALUE: Long = (-1024)

val STRING_VALUE = ("sdbus-c++-testing")
val SIGNATURE_VALUE = Signature("a{is}")
val OBJECT_PATH_VALUE = ObjectPath("/")
val UNIX_FD_VALUE: Int = 0

val DEFAULT_STATE_VALUE = "default-state-value"
val DEFAULT_ACTION_VALUE: UInt = 999u
val DEFAULT_BLOCKING_VALUE = true

val DOUBLE_VALUE = 3.24
