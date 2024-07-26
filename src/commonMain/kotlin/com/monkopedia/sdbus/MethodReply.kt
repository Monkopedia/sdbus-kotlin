@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus

import kotlinx.cinterop.ExperimentalForeignApi

expect class MethodReply : Message {

    fun send()
}
