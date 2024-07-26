/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sdbus-kotlin. If not, see <http://www.gnu.org/licenses/>.
 */
package com.monkopedia.sdbus

/**
 * Emits signal on D-Bus
 *
 * @param signalName Name of the signal
 * @return A helper object for convenient emission of signals
 *
 * This is a high-level, convenience way of emitting D-Bus signals that abstracts
 * from the D-Bus message concept. Signal arguments are automatically serialized
 * in a message and D-Bus signatures automatically deduced from the provided native arguments.
 *
 * @throws [com.monkopedia.sdbus.Error] in case of failure
 */
inline fun Object.emitSignal(
    interfaceName: InterfaceName = InterfaceName(""),
    signalName: SignalName = SignalName(""),
    builder: SignalEmitter.() -> Unit
) = emit(SignalEmitter(interfaceName, signalName).also(builder))

fun Object.emit(signal: SignalEmitter) {
    val m = createSignal(signal.interfaceName, signal.signalName)
    m.serialize(
        signal.typedMethodArguments ?: buildArgs {
            call()
        }
    )
    emitSignal(m)
}

data class SignalEmitter(
    var interfaceName: InterfaceName = InterfaceName(""),
    var signalName: SignalName = SignalName(""),
    var typedMethodArguments: TypedArguments? = null
) : TypedArgumentsBuilderContext() {

    override fun createCall(inputType: InputType, values: List<Any>): TypedArguments =
        super.createCall(inputType, values).also {
            typedMethodArguments = it
        }
}
