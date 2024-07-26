package com.monkopedia.sdbus

/**
 * A handle to resource that may be manually released.
 *
 * While all resources/registrations will be managed automatically and cleaned
 * up once GC'd, that is out of control of the callers. Most registration methods
 * or similar return a [Resource]. Calling [release] will remove any registrations or
 * temporary effects caused by the method that returned the [Resource].
 *
 * Calling [release] multiple times will have no effect. Once [release] is called on
 * an object, that object should no longer be interacted with.
 */
interface Resource {
    /**
     * Releases this resource and any child resources it may have.
     */
    fun release()
}
