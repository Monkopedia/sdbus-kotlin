@file:OptIn(ExperimentalNativeApi::class)

package com.monkopedia.sdbus.internal

import com.monkopedia.sdbus.Resource
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

internal class Reference<T>(val value: T, onLeaveScopes: (T) -> Unit) : Resource {
    private val resource = value to singleCall(onLeaveScopes)

    fun get(): T = value
    private var extraReferences: MutableList<Reference<*>>? = null

    private val cleaner = createCleaner(resource) { (value, onLeaveScopes) ->
        onLeaveScopes.invoke(value)
    }

    override fun release() {
        extraReferences?.forEach { it.release() }
        resource.second(resource.first)
    }

    internal fun <R> freeAfter(value: Reference<R>): Resource {
        (extraReferences ?: mutableListOf<Reference<*>>().also { extraReferences = it })
            .add(value)
        return this
    }
}

private fun <T> singleCall(callback: (T) -> Unit): (T) -> Unit {
    return object : (T) -> Unit {
        private var hasCalled = false
        override fun invoke(p1: T) {
            if (hasCalled) return
            hasCalled = true
            callback(p1)
        }
    }
}
