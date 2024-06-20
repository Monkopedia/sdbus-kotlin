@file:OptIn(ExperimentalForeignApi::class, ExperimentalStdlibApi::class,
    ExperimentalNativeApi::class
)

package com.monkopedia.sdbus.internal

import com.monkopedia.sdbus.internal.ScopedObject.Companion.stdMove
import com.monkopedia.sdbus.internal.ScopedObject.SharedPtr
import com.monkopedia.sdbus.internal.ScopedObject.WeakPtr
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.cinterop.Arena
import kotlinx.cinterop.DeferScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.memScoped

private val scopeTrackingLock = ReentrantLock()

private inline fun debugPrint(msg: () -> String) {
    if (false) println(msg())
//    usleep(1000u)
}

interface Holder<T : ScopedObject> {
    fun own(scope: DeferScope): T
}

class Unowned<T : ScopedObject> internal constructor(private val instance: T) : Holder<T> {
    override fun own(scope: DeferScope): T {
        return instance.also { it.stdAccept(scope) }
    }

    internal fun peek(): T {
        return instance
    }

    fun weak(): WeakPtr<T> {
        return WeakPtr(instance)
    }

}

interface CustomDeferScope {
    fun defer(runnable: () -> Unit)
}

open class ScopedObject {
    private var scopeCount = 1
    private var isMoving = false

    constructor(initialScope: DeferScope) {
        debugPrint { "Create - ${this::class} ${hashCode().toHexString()}" }
        initialScope.defer {
            deref()
        }
    }

    constructor(initialScope: CustomDeferScope) {
        debugPrint { "Create - ${this::class} ${hashCode().toHexString()}" }
        initialScope.defer {
            deref()
        }
    }

    fun stdAccept(scope: DeferScope) = scopeTrackingLock.withLock {
        debugPrint { "Accept move - ${this::class} ${hashCode().toHexString()}" }
        require(scopeCount == 0)
        require(isMoving)
        isMoving = false
        scopeCount = 1
        scope.defer {
            deref()
        }
    }

    private fun stdWeak() = scopeTrackingLock.withLock {
        debugPrint { "Accept move - ${this::class} ${hashCode().toHexString()}" }
        require(scopeCount > 0)
        require(isMoving)
        isMoving = false
    }

    fun useIn(scope: DeferScope) {
        debugPrint { "Use in - ${this::class} ${hashCode().toHexString()}" }
        ref()
        scope.defer {
            deref()
        }
    }

    fun useIn(scope: CustomDeferScope) {
        debugPrint { "Use in - ${this::class} ${hashCode().toHexString()}" }
        ref()
        scope.defer {
            deref()
        }
    }

    private fun ref() = scopeTrackingLock.withLock {
        debugPrint { "Ref - ${this::class} ${hashCode().toHexString()} $isMoving $scopeCount" }
        require(!isMoving) { "Ref moving - ${this::class} ${hashCode().toHexString()} $isMoving $scopeCount" }//"Object cannot be referenced while moving"}
        require(scopeCount > 0) { "Ref count - ${this::class} ${hashCode().toHexString()} $isMoving $scopeCount" }//"Object has no references" }
        scopeCount++
    }

    private fun deref() {
        val isFree = scopeTrackingLock.withLock {
            debugPrint { "Deref - ${this::class} ${hashCode().toHexString()} $isMoving $scopeCount" }
            --scopeCount == 0 && !isMoving
        }
        if (isFree) {
            debugPrint { "Invoking onLeaveScopes ${this::class} ${hashCode().toHexString()} " }
            onLeaveScopes()
        }
    }

    open fun onLeaveScopes() = Unit

    class SharedPtr<T : ScopedObject>(
        private val target: T,
        private val defer: () -> Unit
    ) {
        constructor(target: T) : this(target, ScopeCapture.captureScope(target))

        fun get(): T {
            return target
        }

        fun release() {
            defer.invoke()
        }
    }

    class WeakPtr<T : ScopedObject>(private val target: T) {
        init {
            target.stdWeak()
        }
        fun get(): T? {
            if (expired) {
                return null
            }
            return target
        }

        fun use(scope: DeferScope): T? {
            return get()?.also { it.useIn(scope) }
        }

        val expired: Boolean
            get() = target.scopeCount == 0 && !target.isMoving
    }

    companion object {


        fun <T : ScopedObject> T.stdMove() = scopeTrackingLock.withLock {
            debugPrint { "${this::class} ${hashCode()} start moving $isMoving $scopeCount" }
            isMoving = true
            Unowned(this)
        }
    }
}

inline fun <T : ScopedObject> create(creator: MemScope.() -> T): Unowned<T> =
    memScoped { creator().stdMove() }

inline fun <T : ScopedObject> createShared(creator: CustomDeferScope.() -> T): SharedPtr<T> =
    ScopeCapture.createShared(creator)

open class Scope : ScopedObject {
    constructor(initialScope: DeferScope) : super(initialScope)
    constructor(initialScope: CustomDeferScope) : super(initialScope)

    val scope = Arena()

    final override fun onLeaveScopes() {
        onScopeCleared()
        scope.clear()
    }

    open fun onScopeCleared() = Unit
}

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


object ScopeCapture {
    var lastDefer: (() -> Unit)? = null

    val scopeSaver: CustomDeferScope = object : CustomDeferScope {
        override fun defer(runnable: () -> Unit) {
            lastDefer = runnable
        }
    }

    inline fun <T : ScopedObject> createShared(creator: CustomDeferScope.() -> T): SharedPtr<T> =
        try {
            SharedPtr(
                scopeSaver.let(creator),
                lastDefer ?: error("Item did not register properly")
            )
        } finally {
            lastDefer = null
        }

    fun captureScope(scopedObject: ScopedObject): () -> Unit = try {
        scopedObject.useIn(scopeSaver)
        lastDefer ?: error("Item did not register properly")
    } finally {
        lastDefer = null
    }
}

class MutableScopedList<T : ScopedObject>(
    initialScope: DeferScope,
    private val baseList: MutableList<T> = mutableListOf()
) : ScopedObject(initialScope), List<T> by baseList, MutableList<T> {
    private val defers = mutableListOf<() -> Unit>()

    override fun onLeaveScopes() {
        clear()
    }

    private fun T.enterScope(): () -> Unit = ScopeCapture.captureScope(this)

    override fun add(element: T): Boolean {
        defers.add(element.enterScope())
        baseList.add(element)
        return true
    }

    override fun add(index: Int, element: T) {
        defers.add(index, element.enterScope())
        baseList.add(index, element)
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        defers.addAll(index, elements.map { it.enterScope() })
        return baseList.addAll(index, elements)
    }

    override fun addAll(elements: Collection<T>): Boolean {
        defers.addAll(elements.map { it.enterScope() })
        return baseList.addAll(elements)
    }

    override fun clear() {
        defers.forEach { it.invoke() }
        defers.clear()
        baseList.clear()
    }

    override fun removeAt(index: Int): T {
        defers.removeAt(index).invoke()
        return baseList.removeAt(index)
    }

    override fun set(index: Int, element: T): T {
        val newDefer = element.enterScope()
        defers.getOrNull(index)?.invoke()
        defers[index] = newDefer
        return baseList.set(index, element)
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        val items = baseList.withIndex().reversed()
        var hasChanged = false
        for ((index, item) in items) {
            if (item !in elements) {
                defers.removeAt(index).invoke()
                baseList.removeAt(index)
                hasChanged = true
            }
        }
        return hasChanged
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        val items = baseList.withIndex().reversed()
        var hasChanged = false
        for ((index, item) in items) {
            if (item in elements) {
                defers.removeAt(index).invoke()
                baseList.removeAt(index)
                hasChanged = true
            }
        }
        return hasChanged
    }

    override fun remove(element: T): Boolean {
        val index = baseList.indexOf(element)
        if (index < 0) return false
        defers.removeAt(index).invoke()
        baseList.removeAt(index)
        return true
    }

    override fun iterator(): MutableIterator<T> {
        return object : Iterator<T> by baseList.iterator(), MutableIterator<T> {
            override fun remove() = throw NotImplementedError("Mutation not supported")
        }
    }

    override fun listIterator(): MutableListIterator<T> =
        throw NotImplementedError("List iteration not supported")

    override fun listIterator(index: Int): MutableListIterator<T> =
        throw NotImplementedError("List iteration not supported")

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        val subList = baseList.subList(fromIndex, toIndex)
        return object : List<T> by subList, MutableList<T> {
            override fun add(element: T): Boolean =
                throw NotImplementedError("Mutation not supported")

            override fun add(index: Int, element: T) =
                throw NotImplementedError("Mutation not supported")

            override fun addAll(index: Int, elements: Collection<T>): Boolean =
                throw NotImplementedError("Mutation not supported")

            override fun addAll(elements: Collection<T>): Boolean =
                throw NotImplementedError("Mutation not supported")

            override fun clear() = throw NotImplementedError("Mutation not supported")
            override fun removeAt(index: Int): T =
                throw NotImplementedError("Mutation not supported")

            override fun set(index: Int, element: T): T =
                throw NotImplementedError("Mutation not supported")

            override fun retainAll(elements: Collection<T>): Boolean =
                throw NotImplementedError("Mutation not supported")

            override fun removeAll(elements: Collection<T>): Boolean =
                throw NotImplementedError("Mutation not supported")

            override fun remove(element: T): Boolean =
                throw NotImplementedError("Mutation not supported")

            override fun iterator(): MutableIterator<T> =
                throw NotImplementedError("Sub iterators not supported")

            override fun listIterator(): MutableListIterator<T> =
                throw NotImplementedError("Sub iterators not supported")

            override fun listIterator(index: Int): MutableListIterator<T> =
                throw NotImplementedError("Sub iterators not supported")

            override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> =
                throw NotImplementedError("Sub iterators not supported")
        }
    }

}
