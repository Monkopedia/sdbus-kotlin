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
 * sdbus-kotlin is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * sdbus-kotlin. If not, see <https://www.gnu.org/licenses/>.
 */
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
