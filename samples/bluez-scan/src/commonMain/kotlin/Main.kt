/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2025 Jason Monk <monkopedia@gmail.com>
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
import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.ObjectManagerProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.createSystemBusConnection
import com.monkopedia.sdbus.withService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.runBlocking
import org.bluez.Adapter1Proxy
import org.bluez.Device1
import org.bluez.Device1Proxy

fun main(args: Array<String>) {
    runBlocking {
        val connection = createSystemBusConnection().withService(ServiceName("org.bluez"))
        val objectManager = ObjectManagerProxy(connection.createProxy(ObjectPath("/")))
        val adapter = Adapter1Proxy(connection.createProxy(ObjectPath("/org/bluez/hci0")))
        adapter.startDiscovery()

        // Flow emitting devices on the interface.
        val flow = objectManager.objectsFor(Device1.INTERFACE_NAME)
            .shareIn(this, SharingStarted.Eagerly, replay = 3)
        println("Scanning")

        // Print all currently known devices.
        flow.first().forEach {
            val device = Device1Proxy(connection.createProxy(it))
            println("Found device ${device.name} ${device.address}")
            device.proxy.release()
        }

        // Zip with itself one behind so we can search for new objects
        // in each list.
        flow.zip(flow.drop(1), ::Pair).collect { (last, next) ->
            for (path in next - last.toSet()) {
                val device = Device1Proxy(connection.createProxy(path))
                try {
                    println("Found device ${device.name} ${device.address}")
                } catch (t: Error) {
                    try {
                        println("Found device ${device.address}")
                    } catch (t: Error) {
                        println("Can't handle device $path")
                    }
                }
                device.proxy.release()
            }
        }
    }
}
