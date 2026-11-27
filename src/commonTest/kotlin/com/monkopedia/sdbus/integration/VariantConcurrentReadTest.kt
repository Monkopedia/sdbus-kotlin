package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MethodName
import com.monkopedia.sdbus.ObjectManagerProxy
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.ServiceName
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.addVTable
import com.monkopedia.sdbus.createBusConnection
import com.monkopedia.sdbus.createObject
import com.monkopedia.sdbus.createProxy
import com.monkopedia.sdbus.method
import com.monkopedia.sdbus.prop
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val VALUE = "BF-Test"
private const val CALLERS = 3

/**
 * Regression (#294): reading a [Variant] moves the read cursor of its backing message, so two
 * threads reading the same variant used to race. On native that corrupted the heap
 * (`malloc(): unaligned tcache chunk detected`); on JVM it made most reads fail and left the
 * variant unreadable. [ObjectManagerProxy] hands the same variants to every collector, so this
 * was reachable without the caller ever sharing anything itself (blue-falcon-sdbus#75).
 *
 * Every case runs for a bounded [RUN_TIME] and requires every single read to succeed.
 */
class VariantConcurrentReadTest {

    private class Holder(var value: String)

    private class Counts(val reads: Long, val failures: Long, val firstFailure: Throwable?)

    /**
     * Runs [callers] coroutines on [Dispatchers.Default], released together, each calling [read]
     * until [RUN_TIME] elapses. [read] gets the caller index and the caller's read count.
     */
    private suspend fun hammer(
        callers: Int,
        read: suspend (caller: Int, n: Long) -> String?
    ): Counts = coroutineScope {
        val start = CompletableDeferred<Unit>()
        val results = List(callers) { caller ->
            async(Dispatchers.Default) {
                start.await()
                val mark = TimeSource.Monotonic.markNow()
                var reads = 0L
                var failures = 0L
                var first: Throwable? = null
                while (mark.elapsedNow() < RUN_TIME) {
                    try {
                        val v = read(caller, reads)
                        if (v != VALUE) throw AssertionError("read $v, expected $VALUE")
                    } catch (t: Throwable) {
                        failures++
                        if (first == null) first = t
                    }
                    reads++
                }
                Counts(reads, failures, first)
            }
        }
        start.complete(Unit)
        val all = results.awaitAll()
        Counts(
            all.sumOf { it.reads },
            all.sumOf { it.failures },
            all.firstNotNullOfOrNull { it.firstFailure }
        )
    }

    private fun assertAllReadsSucceeded(counts: Counts) {
        println("VariantConcurrentReadTest reads=${counts.reads} failures=${counts.failures}")
        assertTrue(counts.reads > 0, "no reads ran")
        assertEquals(
            0L,
            counts.failures,
            "${counts.failures} of ${counts.reads} reads failed; first: ${counts.firstFailure}"
        )
    }

    @Test
    fun oneSharedVariantReadByManyCallers() = runBlocking {
        val shared = Variant(VALUE)
        assertAllReadsSucceeded(hammer(CALLERS) { _, _ -> shared.get<String>() })
        assertEquals(VALUE, shared.get<String>())
    }

    @Test
    fun sharedVariantReplacedWhileReadByManyCallers() = runBlocking {
        // Swapping in a fresh variant keeps the readers on a cursor that has not been poisoned
        // yet, which is what turned the race into heap corruption rather than failed reads.
        var shared = Variant(VALUE)
        assertAllReadsSucceeded(
            hammer(CALLERS) { caller, n ->
                if (caller == 0 && n % 64 == 0L) shared = Variant(VALUE)
                shared.get<String>()
            }
        )
    }

    @Test
    fun control_oneCallerReadsASharedVariant() = runBlocking {
        val shared = Variant(VALUE)
        assertAllReadsSucceeded(hammer(1) { _, _ -> shared.get<String>() })
    }

    @Test
    fun control_eachCallerReadsItsOwnVariant() = runBlocking {
        val own = List(CALLERS) { Variant(VALUE) }
        assertAllReadsSucceeded(hammer(CALLERS) { caller, _ -> own[caller].get<String>() })
    }

    @Test
    fun objectManagerCollectorsReadTheSameVariants() = runBlocking {
        val id = Random.nextInt(100_000, 999_999)
        val service = ServiceName("com.monkopedia.sdbus.vcr$id")
        val managerPath = ObjectPath("/com/monkopedia/sdbus/vcr$id")
        val childPath = ObjectPath("/com/monkopedia/sdbus/vcr$id/child")
        val iface = InterfaceName("com.monkopedia.sdbus.vcr$id.Device")
        val nameProp = PropertyName("Name")
        val holder = Holder(VALUE)

        val server = createBusConnection(service)
        val client = createBusConnection()
        val managerObj = createObject(server, managerPath)
        val manager = managerObj.addObjectManager()
        val obj = createObject(server, childPath)
        val reg = obj.addVTable(iface) {
            method(MethodName("Ping")) { call<Unit> { } }
            prop(nameProp) { with(holder::value) }
        }
        server.startEventLoop()
        client.startEventLoop()
        val proxy = createProxy(client, service, managerPath)
        try {
            val objectManager = ObjectManagerProxy(proxy)
            // Re-announce the device while the collectors read, so fresh variants keep
            // replacing the cached ones, as BlueZ does while it scans.
            val announcer = launch(Dispatchers.Default) {
                while (isActive) {
                    obj.emitInterfacesAddedSignal(listOf(iface))
                    delay(5.milliseconds)
                }
            }
            val counts = try {
                hammer(CALLERS) { _, _ ->
                    objectManager.objectData(childPath).first()[iface]?.get(nameProp)
                        ?.get<String>()
                }
            } finally {
                announcer.cancelAndJoin()
            }
            assertAllReadsSucceeded(counts)
        } finally {
            reg.release()
            manager.release()
            managerObj.release()
            proxy.release()
            obj.release()
            client.stopEventLoop()
            server.stopEventLoop()
            client.release()
            server.release()
        }
    }

    private companion object {
        val RUN_TIME = 1_500.milliseconds
    }
}
