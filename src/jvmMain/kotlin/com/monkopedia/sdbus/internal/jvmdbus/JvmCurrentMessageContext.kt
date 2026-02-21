package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.Message

internal object JvmCurrentMessageContext {
    private val local = ThreadLocal<Message?>()

    fun current(): Message? = local.get()

    inline fun <T> withMessage(message: Message, block: () -> T): T {
        val previous = local.get()
        local.set(message)
        return try {
            block()
        } finally {
            local.set(previous)
        }
    }
}
