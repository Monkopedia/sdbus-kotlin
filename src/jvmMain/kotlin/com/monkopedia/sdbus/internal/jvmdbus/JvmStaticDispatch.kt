package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.MethodReply

/**
 * JVM-side static dispatch table for method calls.
 *
 * This is intentionally reflection-free in backend code. Call sites can register
 * concrete typed bindings (typically generated code) keyed by D-Bus interface/member
 * and argument count.
 */
internal object JvmStaticDispatch {
    data class DispatchResult(val reply: MethodReply)

    data class MethodKey(
        val destination: String,
        val objectPath: String,
        val interfaceName: String,
        val methodName: String,
        val argCount: Int
    )

    private val handlers = mutableMapOf<MethodKey, (List<Any?>) -> Any?>()

    fun register(
        objectPath: String,
        interfaceName: String,
        methodName: String,
        argCount: Int,
        destination: String = "",
        handler: (List<Any?>) -> Any?
    ) {
        handlers[MethodKey(destination, objectPath, interfaceName, methodName, argCount)] = handler
    }

    fun unregister(
        objectPath: String,
        interfaceName: String,
        methodName: String,
        argCount: Int,
        destination: String = ""
    ) {
        handlers.remove(MethodKey(destination, objectPath, interfaceName, methodName, argCount))
    }

    fun invokeOrNull(
        objectPath: String,
        interfaceName: String,
        methodName: String,
        args: List<Any?>,
        destination: String? = null
    ): Any? = resolveHandler(
        objectPath = objectPath,
        interfaceName = interfaceName,
        methodName = methodName,
        argCount = args.size,
        destination = destination
    )?.invoke(args)

    fun hasHandler(
        objectPath: String,
        interfaceName: String,
        methodName: String,
        args: List<Any?>,
        destination: String? = null
    ): Boolean = resolveHandler(
        objectPath = objectPath,
        interfaceName = interfaceName,
        methodName = methodName,
        argCount = args.size,
        destination = destination
    ) != null

    private fun resolveHandler(
        objectPath: String,
        interfaceName: String,
        methodName: String,
        argCount: Int,
        destination: String?
    ): ((List<Any?>) -> Any?)? {
        if (!destination.isNullOrEmpty()) {
            val exactKey = MethodKey(destination, objectPath, interfaceName, methodName, argCount)
            handlers[exactKey]?.let { return it }
        }
        val wildcardKey = MethodKey("", objectPath, interfaceName, methodName, argCount)
        handlers[wildcardKey]?.let { return it }
        if (!destination.isNullOrEmpty()) {
            val candidates = handlers.filterKeys { key ->
                key.objectPath == objectPath &&
                    key.interfaceName == interfaceName &&
                    key.methodName == methodName &&
                    key.argCount == argCount
            }.values
            if (candidates.size == 1) {
                return candidates.first()
            }
        }
        return null
    }
}
