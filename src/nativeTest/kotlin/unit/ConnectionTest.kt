@file:OptIn(ExperimentalForeignApi::class)
package com.monkopedia.sdbus

import cnames.structs.sd_bus
import com.monkopedia.sdbus.internal.Connection.Companion.defaultConnection
import com.monkopedia.sdbus.internal.Connection.Companion.sessionConnection
import com.monkopedia.sdbus.internal.Connection.Companion.systemConnection
import com.monkopedia.sdbus.mocks.DefaultResponses.withDefaults
import com.monkopedia.sdbus.mocks.MappingHandler.Companion.configure
import com.monkopedia.sdbus.mocks.RecordingHandler.Companion.record
import com.monkopedia.sdbus.mocks.SdBusMock
import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toCPointer
import com.monkopedia.sdbus.ConnectionTest.ConnectionCreationTest
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.set

typealias ADefaultBusConnection = ConnectionCreationTest;
typealias ASystemBusConnection = ConnectionCreationTest;
typealias ASessionBusConnection = ConnectionCreationTest;

class ConnectionTest {

    class ConnectionCreationTest //: public ::testing::Test
//    {
//        protected:
//        ConnectionCreationTest() = default;
//
//        std::unique_ptr<NiceMock<SdBusMock>> sdBusIntfMock_ = std::make_unique<NiceMock<SdBusMock>>();
//        sd_bus* fakeBusPtr_ = reinterpret_cast<sd_bus*>(1);
//    };
    val sdBusIntfMock_ = SdBusMock().withDefaults()
    val fakeBusPtr_ = 1.toLong().toCPointer<sd_bus>()

    private val openHandler = { type: KType, args: Array<out Any?> ->
        @Suppress("UNCHECKED_CAST")
        (args[1] as CPointer<CPointerVar<sd_bus>>)[0] = fakeBusPtr_
        1
    }

    @Test
    fun `ADefaultBusConnection OpensAndFlushesBusWhenCreated`(): Unit = memScoped {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open) answers openHandler
        }
        val calls = sdBusIntfMock_.record { defaultConnection(it) }
        assertEquals(2, calls.size)
        assertEquals("sd_bus_flush", calls[1].args[0])
    }

    @Test
    fun `ASystemBusConnection OpensAndFlushesBusWhenCreated`(): Unit = memScoped {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_system) answers openHandler
        }
        val calls = sdBusIntfMock_.record { systemConnection(it) }
        assertEquals(2, calls.size)
        assertEquals("sd_bus_flush", calls[1].args[0])
    }

    @Test
    fun `ASessionBusConnection OpensAndFlushesBusWhenCreated`(): Unit = memScoped {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_user) answers openHandler
        }
        val calls = sdBusIntfMock_.record { sessionConnection(it) }
        assertEquals(2, calls.size)
        assertEquals("sd_bus_flush", calls[1].args[0])
    }

    @Test
    fun `ADefaultBusConnection ClosesAndUnrefsBusWhenDestructed`(): Unit {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open) answers openHandler
        }
        val calls = sdBusIntfMock_.record { memScoped { defaultConnection(it) } }
        assertEquals(3, calls.size)
        assertEquals("sd_bus_flush_close_unref", calls[2].args[0])
    }

    @Test
    fun `ASystemBusConnection ClosesAndUnrefsBusWhenDestructed`(): Unit {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_system) answers openHandler
        }
        val calls = sdBusIntfMock_.record { memScoped { systemConnection(it) } }
        assertEquals(3, calls.size)
        assertEquals("sd_bus_flush_close_unref", calls[2].args[0])
    }

    @Test
    fun `ASessionBusConnection ClosesAndUnrefsBusWhenDestructed`(): Unit {
        sdBusIntfMock_.configure {
            method(SdBusMock::sd_bus_open_user) answers openHandler
        }
        val calls = sdBusIntfMock_.record { memScoped { sessionConnection(it) } }
        assertEquals(3, calls.size)
        assertEquals("sd_bus_flush_close_unref", calls[2].args[0])
    }

    @Test
    fun `ADefaultBusConnection ThrowsErrorWhenOpeningTheBusFailsDuringConstruction`(): Unit =
        memScoped {
            sdBusIntfMock_.configure {
                method(SdBusMock::sd_bus_open) returns -1
            }
            try {
                val calls = sdBusIntfMock_.record { defaultConnection(it) }
                fail("Expected failure, not $calls")
            } catch (t: Throwable) {
                // Expected failure
            }
        }

    @Test
    fun `ASystemBusConnection ThrowsErrorWhenOpeningTheBusFailsDuringConstruction`(): Unit =
        memScoped {
            sdBusIntfMock_.configure {
                method(SdBusMock::sd_bus_open_system) returns -1
            }
            try {
                val calls = sdBusIntfMock_.record { systemConnection(it) }
                fail("Expected failure, not $calls")
            } catch (t: Throwable) {
                // Expected failure
            }
        }

    @Test
    fun `ASessionBusConnection ThrowsErrorWhenOpeningTheBusFailsDuringConstruction`(): Unit =
        memScoped {
            sdBusIntfMock_.configure {
                method(SdBusMock::sd_bus_open_user) returns -1
            }
            try {
                val calls = sdBusIntfMock_.record { sessionConnection(it) }
                fail("Expected failure, not $calls")
            } catch (t: Throwable) {
                // Expected failure
            }
        }

    @Test
    fun `ADefaultBusConnection ThrowsErrorWhenFlushingTheBusFailsDuringConstruction`(): Unit =
        memScoped {
            sdBusIntfMock_.configure {
                method(SdBusMock::sd_bus_open) answers openHandler
                method(SdBusMock::sd_bus_flush) returns -1
            }
            try {
                val calls = sdBusIntfMock_.record { defaultConnection(it) }
                fail("Expected failure, not $calls")
            } catch (t: Throwable) {
                // Expected failure
            }
        }

    @Test
    fun `ASystemBusConnection ThrowsErrorWhenFlushingTheBusFailsDuringConstruction`(): Unit =
        memScoped {
            sdBusIntfMock_.configure {
                method(SdBusMock::sd_bus_open_system) answers openHandler
                method(SdBusMock::sd_bus_flush) returns -1
            }
            try {
                val calls = sdBusIntfMock_.record { systemConnection(it) }
                fail("Expected failure, not $calls")
            } catch (t: Throwable) {
                // Expected failure
            }
        }

    @Test
    fun `ASessionBusConnection ThrowsErrorWhenFlushingTheBusFailsDuringConstruction`(): Unit =
        memScoped {
            sdBusIntfMock_.configure {
                method(SdBusMock::sd_bus_open_user) answers openHandler
                method(SdBusMock::sd_bus_flush) returns -1
            }
            try {
                val calls = sdBusIntfMock_.record { sessionConnection(it) }
                fail("Expected failure, not $calls")
            } catch (t: Throwable) {
                // Expected failure
            }
        }

////    namespace {
//        template <typename _BusTypeTag>
//        class AConnectionNameRequest : public ::testing::Test
//        {
//            protected:
//            void setUpBusOpenExpectation();
//            std::unique_ptr<Connection> makeConnection();
//
//            void SetUp() override
//                {
//                    setUpBusOpenExpectation();
//                    ON_CALL(*sdBusIntfMock_, sd_bus_flush(_)).WillByDefault(Return(1));
//                    ON_CALL(*sdBusIntfMock_, sd_bus_flush_close_unref(_)).WillByDefault(Return(fakeBusPtr_));
//                    con_ = makeConnection();
//                }
//
//            NiceMock<SdBusMock>* sdBusIntfMock_ = new NiceMock<SdBusMock>(); // con_ below will assume ownership
//            sd_bus* fakeBusPtr_ = reinterpret_cast<sd_bus*>(1);
//            std::unique_ptr<Connection> con_;
//        };
//
//        template<> void AConnectionNameRequest<Connection::default_bus_t>::setUpBusOpenExpectation()
//        {
//            EXPECT_CALL(*sdBusIntfMock_, sd_bus_open(_)).WillOnce(DoAll(SetArgPointee<0>(fakeBusPtr_), Return(1)));
//        }
//        template<> void AConnectionNameRequest<Connection::system_bus_t>::setUpBusOpenExpectation()
//        {
//            EXPECT_CALL(*sdBusIntfMock_, sd_bus_open_system(_)).WillOnce(DoAll(SetArgPointee<0>(fakeBusPtr_), Return(1)));
//        }
//        template<> void AConnectionNameRequest<Connection::session_bus_t>::setUpBusOpenExpectation()
//        {
//            EXPECT_CALL(*sdBusIntfMock_, sd_bus_open_user(_)).WillOnce(DoAll(SetArgPointee<0>(fakeBusPtr_), Return(1)));
//        }
//        template<> void AConnectionNameRequest<Connection::custom_session_bus_t>::setUpBusOpenExpectation()
//        {
//            EXPECT_CALL(*sdBusIntfMock_, sd_bus_open_user_with_address(_, _)).WillOnce(DoAll(SetArgPointee<0>(fakeBusPtr_), Return(1)));
//        }
//        template<> void AConnectionNameRequest<Connection::remote_system_bus_t>::setUpBusOpenExpectation()
//        {
//            EXPECT_CALL(*sdBusIntfMock_, sd_bus_open_system_remote(_, _)).WillOnce(DoAll(SetArgPointee<0>(fakeBusPtr_), Return(1)));
//        }
//        template<> void AConnectionNameRequest<Connection::pseudo_bus_t>::setUpBusOpenExpectation()
//        {
//            EXPECT_CALL(*sdBusIntfMock_, sd_bus_new(_)).WillOnce(DoAll(SetArgPointee<0>(fakeBusPtr_), Return(1)));
//            // `sd_bus_start` for pseudo connection shall return an error value, remember this is a fake connection...
//            EXPECT_CALL(*sdBusIntfMock_, sd_bus_start(fakeBusPtr_)).WillOnce(Return(-EINVAL));
//        }
//        template <typename _BusTypeTag>
//        std::unique_ptr<Connection> AConnectionNameRequest<_BusTypeTag>::makeConnection()
//        {
//            return std::make_unique<Connection>(std::unique_ptr<NiceMock<SdBusMock>>(sdBusIntfMock_), _BusTypeTag{});
//        }
//        template<> std::unique_ptr<Connection> AConnectionNameRequest<Connection::custom_session_bus_t>::makeConnection()
//        {
//            return std::make_unique<Connection>(std::unique_ptr<NiceMock<SdBusMock>>(sdBusIntfMock_), Connection::custom_session_bus, "custom session bus");
//        }
//        template<> std::unique_ptr<Connection> AConnectionNameRequest<Connection::remote_system_bus_t>::makeConnection()
//        {
//            return std::make_unique<Connection>(std::unique_ptr<NiceMock<SdBusMock>>(sdBusIntfMock_), Connection::remote_system_bus, "some host");
//        }
//
//        typedef ::testing::Types< Connection::default_bus_t
//        , Connection::system_bus_t
//        , Connection::session_bus_t
//        , Connection::custom_session_bus_t
//        , Connection::remote_system_bus_t
//        , Connection::pseudo_bus_t
//        > BusTypeTags;
//
//        TYPED_TEST_SUITE(AConnectionNameRequest, BusTypeTags);
//    }
//
//    TYPED_TEST(AConnectionNameRequest, DoesNotThrowOnSuccess)
//    {
//        EXPECT_CALL(*this->sdBusIntfMock_, sd_bus_request_name(_, _, _)).WillOnce(Return(1));
//        sdbus::ConnectionName name{"org.sdbuscpp.somename"};
//
//        this->con_->requestName(name);
//    }
//
//    TYPED_TEST(AConnectionNameRequest, ThrowsOnFail)
//    {
//        sdbus::ConnectionName name{"org.sdbuscpp.somename"};
//
//        EXPECT_CALL(*this->sdBusIntfMock_, sd_bus_request_name(_, _, _)).WillOnce(Return(-1));
//
//        ASSERT_THROW(this->con_->requestName(name), sdbus::Error);
//    }
////}
}