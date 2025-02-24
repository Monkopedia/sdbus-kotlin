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
package com.monkopedia.sdbus.integration

import com.monkopedia.sdbus.ObjectManagerProxy
import com.monkopedia.sdbus.createProxy
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import platform.posix.usleep

class SdbusConnectionFixture(test: BaseTest) : BaseTestFixture(test) {

    private var objectManagerAdaptor: ObjectManagerTestAdaptor? = null

    var adaptor: TestAdaptor? = null
    var proxy: TestProxy? = null
    var objectManagerProxy: ObjectManagerProxy? = null

    override fun onBeforeTest() {
        globalAdaptorConnection.requestName(SERVICE_NAME)
        globalAdaptorConnection.enterEventLoopAsync()
        globalProxyConnection.enterEventLoopAsync()
        runBlocking {
            delay(50.milliseconds)
        }
        objectManagerProxy = ObjectManagerProxy(
            createProxy(
                globalProxyConnection,
                SERVICE_NAME,
                MANAGER_PATH
            )
        )
        proxy = TestProxy(globalProxyConnection, SERVICE_NAME, OBJECT_PATH)
        proxy?.registerProxy()

        objectManagerAdaptor = ObjectManagerTestAdaptor(
            globalAdaptorConnection,
            MANAGER_PATH
        )
        adaptor = TestAdaptor(globalAdaptorConnection, OBJECT_PATH)
        adaptor?.registerAdaptor()
    }

    override fun onAfterTest() {
        adaptor?.obj?.release()
        proxy?.proxy?.release()
        objectManagerAdaptor?.obj?.release()
        objectManagerProxy?.proxy?.release()
        adaptor = null
        proxy = null
        objectManagerProxy = null
        objectManagerAdaptor = null
        usleep(1000u)
        globalAdaptorConnection.releaseName(SERVICE_NAME)
        runBlocking {
            globalAdaptorConnection.leaveEventLoop()
            globalProxyConnection.leaveEventLoop()
        }
    }
}
