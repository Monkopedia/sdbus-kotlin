package com.monkopedia.sdbus

import java.util.concurrent.atomic.AtomicBoolean

internal object NoOpResource : Resource {
    override fun release() = Unit
}

internal class CloseableResource(private val closeable: AutoCloseable) : Resource {
    override fun release() {
        runCatching { closeable.close() }
    }
}

internal class ActionResource(private val action: () -> Unit) : Resource {
    private val released = AtomicBoolean(false)

    override fun release() {
        if (released.compareAndSet(false, true)) {
            action()
        }
    }
}
