package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.jvmdbus.JvmDbusBackendProvider

internal class JvmObject(
    override val connection: Connection,
    override val objectPath: ObjectPath,
    private val backend: com.monkopedia.sdbus.internal.jvmdbus.JvmDbusObject
) : Object {
    override fun emitPropertiesChangedSignal(
        interfaceName: InterfaceName,
        propNames: List<PropertyName>
    ): Unit = backend.emitPropertiesChangedSignal(interfaceName, propNames)

    override fun emitPropertiesChangedSignal(interfaceName: InterfaceName): Unit =
        backend.emitPropertiesChangedSignal(interfaceName)

    override fun emitInterfacesAddedSignal(): Unit = backend.emitInterfacesAddedSignal()

    override fun emitInterfacesAddedSignal(interfaces: List<InterfaceName>): Unit =
        backend.emitInterfacesAddedSignal(interfaces)

    override fun emitInterfacesRemovedSignal(): Unit = backend.emitInterfacesRemovedSignal()

    override fun emitInterfacesRemovedSignal(interfaces: List<InterfaceName>): Unit =
        backend.emitInterfacesRemovedSignal(interfaces)

    override fun addObjectManager(): Resource = backend.addObjectManager()

    override val currentlyProcessedMessage: Message
        get() = backend.currentlyProcessedMessage()

    override fun addVTable(interfaceName: InterfaceName, vtable: List<VTableItem>): Resource =
        backend.addVTable(interfaceName, vtable)

    override fun createSignal(interfaceName: InterfaceName, signalName: SignalName): Signal =
        backend.createSignal(interfaceName, signalName)

    override fun emitSignal(message: Signal): Unit = backend.emitSignal(message)

    override fun release(): Unit = backend.release()
}

actual fun createObject(connection: Connection, objectPath: ObjectPath): Object = JvmObject(
    connection,
    objectPath,
    JvmDbusBackendProvider.backend.createObject(connection, objectPath)
)
