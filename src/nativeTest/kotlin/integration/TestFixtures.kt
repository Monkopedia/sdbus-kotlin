
package com.monkopedia.sdbus.integration

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import platform.posix.usleep

abstract class ConnectionTestFixture(test: BaseTest) : BaseTestFixture(test) {

    private var m_objectManagerAdaptor: ObjectManagerTestAdaptor? = null

    var m_adaptor: TestAdaptor? = null
    var m_proxy: TestProxy? = null
    var m_objectManagerProxy: ObjectManagerTestProxy? = null

    override fun onBeforeTest() {
        m_objectManagerProxy = ObjectManagerTestProxy(
            s_proxyConnection,
            SERVICE_NAME,
            MANAGER_PATH
        );
        m_proxy = TestProxy(s_proxyConnection, SERVICE_NAME, OBJECT_PATH)
        m_proxy?.registerProxy()

        m_objectManagerAdaptor = ObjectManagerTestAdaptor(
            s_adaptorConnection,
            MANAGER_PATH
        )
        m_adaptor = TestAdaptor(s_adaptorConnection, OBJECT_PATH)
        m_adaptor?.registerAdaptor()
    }

    override fun onAfterTest() {
        m_adaptor?.obj?.release()
        m_proxy?.proxy?.release()
        m_objectManagerAdaptor?.obj?.release()
        m_objectManagerProxy?.proxy?.release()
        m_adaptor = null
        m_proxy = null
        m_objectManagerProxy = null
        m_objectManagerAdaptor = null
        usleep(1000u)
    }
}

class TestFixtureSdBusCppLoop(test: BaseTest) : ConnectionTestFixture(test) {

    override fun onBeforeTest() {
        s_adaptorConnection.requestName(SERVICE_NAME)
        s_adaptorConnection.enterEventLoopAsync()
        s_proxyConnection.enterEventLoopAsync()
        runBlocking {
            delay(50.milliseconds)
        }
        super.onBeforeTest()
    }

    override fun onAfterTest() {
        super.onAfterTest()
        s_adaptorConnection.releaseName(SERVICE_NAME)
        runBlocking {
            s_adaptorConnection.leaveEventLoop();
            s_proxyConnection.leaveEventLoop();
        }
    }
};
