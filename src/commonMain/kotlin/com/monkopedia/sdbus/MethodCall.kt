
package com.monkopedia.sdbus

expect class MethodCall : Message {

    fun send(timeout: ULong): MethodReply

    fun createReply(): MethodReply

    fun createErrorReply(error: Error): MethodReply

    var dontExpectReply: Boolean
}
