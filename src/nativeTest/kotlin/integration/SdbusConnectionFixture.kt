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
