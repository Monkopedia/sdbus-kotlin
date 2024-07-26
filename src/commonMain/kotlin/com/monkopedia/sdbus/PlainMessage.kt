package com.monkopedia.sdbus

// Represents any of the above message types, or just a message that serves as a container for data
expect class PlainMessage : Message {

    companion object {
        fun createPlainMessage(): PlainMessage
    }
}
