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
@file:OptIn(ExperimentalNativeApi::class)

package com.monkopedia.sdbus

import com.monkopedia.sdbus.internal.AsyncCallInfo
import com.monkopedia.sdbus.internal.ProxyImpl
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

/********************************************/
/**
 * @class PendingAsyncCall
 *
 * PendingAsyncCall represents a simple handle type to cancel the delivery
 * of the asynchronous D-Bus call result to the application.
 *
 * The handle is lifetime-independent from the originating Proxy object.
 * It's safe to call its methods even after the Proxy has gone.
 *
 ***********************************************/
actual class PendingAsyncCall internal constructor(
    private val target: WeakReference<AsyncCallInfo>
) : Resource {

    /**
     * Cancels the delivery of the pending asynchronous call result
     *
     * This function effectively removes the callback handler registered to the
     * async D-Bus method call result delivery. Does nothing if the call was
     * completed already, or if the originating Proxy object has gone meanwhile.
     */
    actual override fun release() {
        val asyncCallInfo = target.get() ?: return
        (asyncCallInfo.proxy as ProxyImpl).erase(asyncCallInfo)
    }

    /**
     * Answers whether the asynchronous call is still pending
     *
     * @return True if the call is pending, false if the call has been fully completed
     *
     * Pending call in this context means a call whose results have not arrived, or
     * have arrived and are currently being processed by the callback handler.
     */
    actual fun isPending(): Boolean = target.get()?.finished == false
}
