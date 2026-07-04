package com.monkopedia.sdbus.internal.jvmdbus

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmStaticDispatchTest {

    // The dispatch table is mutated on user threads (addVTable/release) while dispatch reads
    // (hasHandler/hasMember/invokeOrNull) run concurrently on serve-worker, reader and caller
    // threads. With an unsynchronized map, register/unregister racing a resolve that iterates the
    // key set throws ConcurrentModificationException (or returns a spurious null). This guards the
    // fix. #141.
    @Test
    fun concurrentRegisterUnregisterAndResolveIsRaceFree() {
        val threadCount = 8
        val iterations = 4000
        val errors = CopyOnWriteArrayList<Throwable>()
        val pool = Executors.newFixedThreadPool(threadCount)
        val done = CountDownLatch(threadCount)
        repeat(threadCount) { t ->
            pool.execute {
                try {
                    repeat(iterations) { i ->
                        val path = "/race/${(t * iterations + i) % 40}"
                        if (i % 2 == 0) {
                            JvmStaticDispatch.register(path, "org.race.I", "M", 1, "dest") { it }
                            JvmStaticDispatch.hasMember(path, "org.race.I", "M", "dest")
                            JvmStaticDispatch.invokeOrNull(
                                path,
                                "org.race.I",
                                "M",
                                listOf(1),
                                "dest"
                            )
                            JvmStaticDispatch.unregister(path, "org.race.I", "M", 1, "dest")
                        } else {
                            JvmStaticDispatch.hasHandler(path, "org.race.I", "M", listOf(1), "dest")
                            JvmStaticDispatch.hasMember(path, "org.race.I", "M", "dest")
                        }
                    }
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    done.countDown()
                }
            }
        }
        assertTrue(done.await(60, TimeUnit.SECONDS), "workers did not finish")
        pool.shutdownNow()
        assertTrue(errors.isEmpty(), "concurrent dispatch access threw: ${errors.firstOrNull()}")
    }

    @Test
    fun invokeOrNull_returnsHandlerResultForRegisteredKey() {
        JvmStaticDispatch.register(
            objectPath = "/org/example/dispatch",
            interfaceName = "org.example.Dispatch",
            methodName = "Join",
            argCount = 2
        ) { args ->
            "${args[0]}:${args[1]}"
        }
        try {
            val result = JvmStaticDispatch.invokeOrNull(
                objectPath = "/org/example/dispatch",
                interfaceName = "org.example.Dispatch",
                methodName = "Join",
                args = listOf("left", "right")
            )

            assertEquals("left:right", result)
        } finally {
            JvmStaticDispatch.unregister(
                objectPath = "/org/example/dispatch",
                interfaceName = "org.example.Dispatch",
                methodName = "Join",
                argCount = 2
            )
        }
    }

    @Test
    fun invokeOrNull_returnsNullAfterUnregister() {
        JvmStaticDispatch.register(
            objectPath = "/org/example/dispatch/remove",
            interfaceName = "org.example.Dispatch",
            methodName = "Noop",
            argCount = 0
        ) { _ -> "ignored" }

        JvmStaticDispatch.unregister(
            objectPath = "/org/example/dispatch/remove",
            interfaceName = "org.example.Dispatch",
            methodName = "Noop",
            argCount = 0
        )

        val result = JvmStaticDispatch.invokeOrNull(
            objectPath = "/org/example/dispatch/remove",
            interfaceName = "org.example.Dispatch",
            methodName = "Noop",
            args = emptyList()
        )

        assertNull(result)
    }
}
