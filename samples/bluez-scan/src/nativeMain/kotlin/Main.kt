import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.InterfaceName
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
