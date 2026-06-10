/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2025 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * sdbus-kotlin. If not, see <https://www.gnu.org/licenses/>.
 */

package com.monkopedia.sdbus

/**
 * A [Message] representing a D-Bus method call.
 *
 * Created via [Proxy.createMethodCall]. Serialize the call arguments into it, then dispatch it with
 * [Proxy.callMethod]/[Proxy.callMethodAsync] or [send]. On the receiving (server) side, use
 * [createReply] or [createErrorReply] to build the response.
 */
expect class MethodCall : Message {

    /**
     * Sends this call and waits for the reply.
     *
     * @param timeout Call timeout in microseconds; `0` uses the connection default
     * @return The reply message
     * @throws [com.monkopedia.sdbus.Error] in case of failure
     */
    fun send(timeout: ULong): MethodReply

    /** Creates an empty success reply for this call. */
    fun createReply(): MethodReply

    /**
     * Creates an error reply for this call carrying the given [error].
     *
     * @param error The error to report back to the caller
     */
    fun createErrorReply(error: Error): MethodReply

    /** Whether this call is flagged to not expect a reply. */
    var dontExpectReply: Boolean
}
