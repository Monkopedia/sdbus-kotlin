@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import kotlinx.cinterop.ExperimentalForeignApi
import sdbus.sd_bus_message_handler_t

expect class MethodCall : Message {

    fun send(timeout: ULong): MethodReply

    fun send(callback: sd_bus_message_handler_t, userData: Any?, timeout: ULong): Resource

    fun createReply(): MethodReply

    fun createErrorReply(error: Error): MethodReply

    var dontExpectReply: Boolean
}
