@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.sdbus.integration

import kotlin.native.runtime.NativeRuntimeApi
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

abstract class ConnectionTestFixture(test: BaseTest) : BaseTestFixture(test) {

    private var m_objectManagerAdaptor: ObjectManagerTestAdaptor? = null

    //    var m_adaptorConnection: IConnection? = null
//    var m_proxyConnection: IConnection? = null
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
//        m_objectManagerAdaptor = std::make_unique<ObjectManagerTestAdaptor>(*s_adaptorConnection, MANAGER_PATH);
        m_adaptor = TestAdaptor(s_adaptorConnection, OBJECT_PATH)
        m_adaptor?.registerAdaptor()
    }

    override fun onScopeClosed() {
        m_adaptor?.m_object?.unregister()
        m_proxy?.m_proxy?.unregister()
        m_objectManagerAdaptor?.m_object?.unregister()
        m_objectManagerProxy?.m_proxy?.unregister()
        m_adaptor = null
        m_proxy = null
        m_objectManagerProxy = null
        m_objectManagerAdaptor = null
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

    override fun onScopeClosed() {
        super.onScopeClosed()
        s_adaptorConnection.releaseName(SERVICE_NAME)
        runBlocking {
            s_adaptorConnection.leaveEventLoop();
            s_proxyConnection.leaveEventLoop();
        }
    }
};
