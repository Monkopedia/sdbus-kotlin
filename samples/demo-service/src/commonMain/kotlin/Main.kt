/**
 * Server-side sample for sdbus-kotlin.
 *
 * Exports an object on the (session) bus that demonstrates the adaptor/server half of the
 * library — the part the bluez-scan sample, which is purely a client, does not show:
 *
 *   - a method (`Greet`),
 *   - a read/write property (`Prefix`) that emits `PropertiesChanged` when written,
 *   - a signal (`Tick`) emitted once per second,
 *   - the full lifecycle: createBusConnection(name) -> startEventLoop -> addObjectManager
 *     -> createObject -> register the generated vtable -> ... -> clean shutdown.
 *
 * The interface is described in src/dbusMain/DemoService.xml and turned into the
 * `DemoService1`, `DemoService1Adaptor`, and `DemoService1Proxy` types by the sdbus-kotlin
 * codegen plugin (generateAdapters + generateProxies).
 *
 * Run modes:
 *   (no args)   run the service forever (until Ctrl-C)
 *   <seconds>   run the service for N seconds then shut down cleanly
 *   client      connect to a running service, call it, and print three ticks
 */
import com.monkopedia.demo.DemoService1
import com.monkopedia.demo.DemoService1Adaptor
import com.monkopedia.demo.DemoService1Proxy
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.Object
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.SignalName
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import com.monkopedia.sdbus.signal
import com.monkopedia.sdbus.withService
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val SERVICE_NAME = ServiceName("com.monkopedia.demo")
private val MANAGER_PATH = ObjectPath("/com/monkopedia/demo")
private val OBJECT_PATH = ObjectPath("/com/monkopedia/demo/service")

/**
 * Concrete implementation of the generated [DemoService1Adaptor].
 *
 * We subclass the generated adaptor (so we reuse its interface, its `onTick` signal emitter,
 * and `INTERFACE_NAME`) but override [register] to install the vtable with the explicit
 * [com.monkopedia.sdbus.prop] / withGetter / withSetter DSL. Binding the property callbacks
 * directly is what lets the setter emit `PropertiesChanged`, and it keeps the value
 * serialization explicit on both the JVM and native targets.
 */
private class DemoServiceImpl(obj: Object) : DemoService1Adaptor(obj) {
    private var backingPrefix: String = "Hello"

    override var prefix: String
        get() = backingPrefix
        set(value) = updatePrefix(value)

    override suspend fun greet(name: String): String = "$backingPrefix, $name!"

    private fun updatePrefix(value: String) {
        if (value == backingPrefix) return
        backingPrefix = value
        // Notify subscribers (e.g. `busctl --user monitor`, or a PropertiesProxy.changes()
        // flow) that the property changed.
        obj.emitPropertiesChangedSignal(
            DemoService1.INTERFACE_NAME,
            listOf(PropertyName("Prefix"))
        )
    }

    override fun register() {
        obj.addVTable(DemoService1.INTERFACE_NAME) {
            method(MethodName("Greet")) {
                inputParamNames = listOf("name")
                outputParamNames = listOf("greeting")
                asyncCall(this@DemoServiceImpl::greet)
            }
            signal(SignalName("Tick")) {
                with<ULong>("count")
            }
            prop(PropertyName("Prefix")) {
                withGetter { backingPrefix }
                withSetter<String> { updatePrefix(it) }
            }
        }
    }
}

fun main(args: Array<String>): Unit = runBlocking {
    when (val mode = args.firstOrNull()) {
        "client" -> runClient()
        else -> runServer(mode?.toIntOrNull())
    }
}

private suspend fun runServer(runForSeconds: Int?) = coroutineScope {
    println("Starting demo service, requesting name ${SERVICE_NAME.value} ...")
    val connection = createBusConnection(SERVICE_NAME)
    connection.startEventLoop()

    // Install an ObjectManager so clients can discover the exported object via
    // GetManagedObjects / InterfacesAdded.
    val managerRegistration = connection.addObjectManager(MANAGER_PATH)

    val obj = createObject(connection, OBJECT_PATH)
    val service = DemoServiceImpl(obj)
    // Registers the generated vtable (method Greet, property Prefix, signal Tick) on the object.
    // Because it is registered under the ObjectManager installed above, the object shows up in
    // GetManagedObjects (try `busctl --user call ... GetManagedObjects`).
    service.register()

    // Emit the Tick signal once per second with a monotonically increasing counter.
    val ticker = launch {
        var count = 0uL
        while (isActive) {
            delay(1.seconds)
            service.onTick(++count)
        }
    }

    println("Exported ${DemoService1.INTERFACE_NAME.value} at ${OBJECT_PATH.value}")
    println("Poke it with, e.g.:")
    println("  busctl --user introspect ${SERVICE_NAME.value} ${OBJECT_PATH.value}")
    println(
        "  busctl --user call ${SERVICE_NAME.value} ${OBJECT_PATH.value} " +
            "${DemoService1.INTERFACE_NAME.value} Greet s world"
    )

    try {
        if (runForSeconds != null) {
            println("Running for $runForSeconds s, then shutting down cleanly.")
            delay(runForSeconds.seconds)
        } else {
            println("Press Ctrl-C to stop.")
            awaitCancellation()
        }
    } finally {
        println("Shutting down ...")
        ticker.cancel()
        obj.release()
        managerRegistration.release()
        connection.stopEventLoop()
        connection.release()
    }
}

private suspend fun runClient() = coroutineScope {
    println("Connecting to ${SERVICE_NAME.value} ...")
    val connection = createBusConnection()
    val proxy = connection.withService(SERVICE_NAME).createProxy(OBJECT_PATH)
    val demo = DemoService1Proxy(proxy)

    // Observe the next three ticks in the background.
    val ticks = launch {
        demo.tick.take(3).collect { println("tick -> $it") }
    }

    println("Prefix is currently: \"${demo.prefix}\"")
    println("greet(\"world\") -> \"${demo.greet("world")}\"")

    println("Setting Prefix to \"Hi\" (server should emit PropertiesChanged) ...")
    demo.prefix = "Hi"
    println("greet(\"world\") -> \"${demo.greet("world")}\"")

    ticks.join()

    proxy.release()
    connection.release()
}
