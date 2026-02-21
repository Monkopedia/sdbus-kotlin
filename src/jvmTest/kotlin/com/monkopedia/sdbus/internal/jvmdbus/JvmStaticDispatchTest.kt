package com.monkopedia.sdbus.internal.jvmdbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmStaticDispatchTest {
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
