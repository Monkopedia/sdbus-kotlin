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
package unit

import com.monkopedia.sdbus.BusName
import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.MemberName
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.signatureOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable

@Serializable
private data class A(
    val objPath: ObjectPath,
    val arr: Array<Short>,
    val bool: Boolean,
    val variant: Variant,
    val map: Map<Int, String>
)

@Serializable
private data class B(
    val map: Map<UByte, List<A>>,
    val sig: Signature,
    val unixFd: UnixFd,
    val str: String,
    val str2: String
)
private typealias ComplexTypeCheck = Map<ULong, B>

class TypTraitsTest {

    inline fun <reified T> assertType(value: String) {
        assertEquals(value, signatureOf<T>().value)
    }

    @Serializable
    enum class SomeEnumClass {
        A,
        B,
        C
    }

    @Serializable
    enum class SomeEnumStruct(val long: Long) {
        A(0L),
        B(1L),
        C(2L)
    }

    @Serializable
    enum class SomeClassicEnum {
        A,
        B,
        C
    }

    @Serializable
    data class BoolStruct(val bool: Boolean)

    @Serializable
    data class WideStruct(
        val short: UShort,
        val double: Double,
        val str: String,
        val variant: Variant
    )

    @Test
    fun testbool() = assertType<Boolean>("b")

    @Test
    fun testuint8_t() = assertType<UByte>("y")

    @Test
    fun testint16_t() = assertType<Short>("n")

    @Test
    fun testuint16_t() = assertType<UShort>("q")

    @Test
    fun testint32_t() = assertType<Int>("i")

    @Test
    fun testuint32_t() = assertType<UInt>("u")

    @Test
    fun testint64_t() = assertType<Long>("x")

    @Test
    fun testuint64_t() = assertType<ULong>("t")

    @Test
    fun testdouble() = assertType<Double>("d")

    @Test
    fun testString() = assertType<String>("s")

    @Test
    fun testBusName() = assertType<BusName>("s")

    @Test
    fun testInterfaceName() = assertType<InterfaceName>("s")

    @Test
    fun testMemberName() = assertType<MemberName>("s")

    @Test
    fun testObjectPath() = assertType<ObjectPath>("o")

    @Test
    fun testSignature() = assertType<Signature>("g")

    @Test
    fun testVariant() = assertType<Variant>("v")

    @Test
    fun testUnixFd() = assertType<UnixFd>("h")

    @Test
    fun testStructBool() = assertType<BoolStruct>("(b)")

    @Test
    fun testStruct_uint16_t_double_string_Variant() = assertType<WideStruct>("(qdsv)")

    @Test
    fun testListShort() = assertType<List<Short>>("an")

    @Test
    fun testArrayShort() = assertType<Array<Short>>("an")

//    @Test
    fun testSomeEnumClass() = assertType<SomeEnumClass>("y")

//    @Test
    fun testSomeEnumStruct() = assertType<SomeEnumStruct>("x")

//    @Test
    fun testSomeClassicEnum() = assertType<SomeClassicEnum>("u")

    @Test
    fun testMapIntLong() = assertType<Map<Int, Long>>("a{ix}")

    @Test
    fun testMapType() = assertType<HashMap<Int, Long>>("a{ix}")

    @Test
    fun testComplexType() = assertType<ComplexTypeCheck>("a{t(a{ya(oanbva{is})}ghss)}")
}
