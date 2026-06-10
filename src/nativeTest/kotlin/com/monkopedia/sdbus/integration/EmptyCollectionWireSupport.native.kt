package com.monkopedia.sdbus.integration

internal actual object EmptyCollectionWireSupport {
    // sd-bus carries the real wire signature on the received message.
    actual val preservesWireSignatureForEmptyCollections: Boolean = true
}
