/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sdbus-kotlin. If not, see <http://www.gnu.org/licenses/>.
 */
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Signature
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

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
