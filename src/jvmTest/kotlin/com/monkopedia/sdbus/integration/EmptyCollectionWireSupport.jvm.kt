package com.monkopedia.sdbus.integration

internal actual object EmptyCollectionWireSupport {
    // The pure-Java backend reconstructs the body as plain Kotlin values, so a received empty
    // collection can only be peeked back as the bare container type.
    actual val preservesWireSignatureForEmptyCollections: Boolean = false
}
