@file:OptIn(ExperimentalNativeApi::class)

package com.monkopedia.sdbus.internal

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

class Reference<T>(
    val value: T,
    onLeaveScopes: (T) -> Unit
//    val throwable: Throwable = Throwable()
) {
    private val resource = value to onLeaveScopes
    fun get(): T = value
    private var extraReferences: MutableList<Reference<*>>? = null

    private val cleaner = createCleaner(resource) { (value, onLeaveScopes) ->
        onLeaveScopes.invoke(value)
    }

    fun <R> freeAfter(value: Reference<R>): Slot {
        (extraReferences ?: mutableListOf<Reference<*>>().also { extraReferences = it })
            .add(value)
        return this
    }
}


