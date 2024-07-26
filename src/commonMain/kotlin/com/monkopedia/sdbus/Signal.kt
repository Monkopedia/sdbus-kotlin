
package com.monkopedia.sdbus

expect class Signal : Message {

    fun setDestination(destination: String)

    fun send()
}
