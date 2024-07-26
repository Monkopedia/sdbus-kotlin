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
package com.monkopedia.sdbus.unit

import com.monkopedia.sdbus.Error
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PlainMessage.Companion.createPlainMessage
import com.monkopedia.sdbus.Signature
import com.monkopedia.sdbus.UnixFd
import com.monkopedia.sdbus.Variant
import com.monkopedia.sdbus.containsValueOfType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import platform.linux.EFD_NONBLOCK
import platform.linux.EFD_SEMAPHORE
import platform.linux.eventfd
import platform.posix.close

private const val ANY_UINT64: ULong = 84578348354u
private const val ANY_DOUBLE: Double = 3.14
private typealias TypesComplexType = Map<ULong, List<Pair<String, Double>>>
private typealias TypeWithVariants = List<Pair<Variant, Double>>

class TypesTest {

    /*-------------------------------------*/
    /* --          TEST CASES           -- */
    /*-------------------------------------*/

    @Test
    fun aVariant_CanBeDefaultConstructed() {
        Variant()
    }

    @Test
    fun aVariant_ContainsNoValueAfterDefaultConstructed() {
        val v = Variant()

        assertTrue(v.isEmpty)
    }

    @Test
    fun aVariant_CanBeConstructedFromASimpleValue() {
        val value = 5

        Variant(value)
    }

    @Test
    fun aVariant_CanBeConstructedFromAComplexValue() {
        val value: TypesComplexType =
            mapOf(ANY_UINT64 to listOf("hello" to ANY_DOUBLE, "world" to ANY_DOUBLE))

        Variant(value)
    }

    @Test
    fun aVariant_IsNotEmptyWhenContainsAValue() {
        val v = Variant("hello")

        assertFalse(v.isEmpty)
    }

    @Test
    fun aSimpleVariant_ReturnsTheSimpleValueWhenAsked() {
        val value = 5

        val variant = Variant(value)

        assertEquals(value, variant.get<Int>())
    }

    @Test
    fun aComplexVariant_ReturnsTheComplexValueWhenAsked() {
        val value: TypesComplexType =
            mapOf(ANY_UINT64 to listOf("hello" to ANY_DOUBLE, "world" to ANY_DOUBLE))

        val variant = Variant(value)

        assertEquals(value, variant.get<TypesComplexType>())
    }

    @Test
    fun aVariant_HasConceptuallyNonmutableGetMethodWhichCanBeCalledXTimes() {
        val value = "I am a string"
        val variant = Variant(value)

        assertEquals(value, variant.get<String>())
        assertEquals(value, variant.get<String>())
        assertEquals(value, variant.get<String>())
    }

    @Test
    fun aVariant_ReturnsTrueWhenAskedIfItContainsTheTypeItReallyContains() {
        val value: TypesComplexType =
            mapOf(ANY_UINT64 to listOf("hello" to ANY_DOUBLE, "world" to ANY_DOUBLE))

        val variant = Variant(value)

        assertTrue(variant.containsValueOfType<TypesComplexType>())
    }

    @Test
    fun aSimpleVariant_ReturnsFalseWhenAskedIfItContainsTypeItDoesntReallyContain() {
        val value = 5

        val variant = Variant(value)

        assertFalse(variant.containsValueOfType<Double>())
    }

    @Test
    fun aVariant_CanContainOtherEmbeddedVariants() {
        val value: TypeWithVariants = listOf(
            Variant("a string") to ANY_DOUBLE,
            Variant(ANY_UINT64) to ANY_DOUBLE
        )

        val variant = Variant(value)

        assertTrue(variant.containsValueOfType<TypeWithVariants>())
    }

    @Test
    fun aNonEmptyVariant_SerializesSuccessfullyToAMessage() {
        val variant = Variant("a string")

        val msg = createPlainMessage()

        variant.serializeTo(msg)
    }

    @Test
    fun anEmptyVariant_ThrowsWhenBeingSerializedToAMessage() {
        val variant = Variant()

        val msg = createPlainMessage()

        try {
            variant.serializeTo(msg)
        } catch (t: Error) {
            // Expected failure
        }
    }

    @Test
    fun aNonEmptyVariant_SerializesToAndDeserializesFromAMessageSuccessfully() {
        val value: TypesComplexType =
            mapOf(ANY_UINT64 to listOf("hello" to ANY_DOUBLE, "world" to ANY_DOUBLE))
        val variant = Variant(value)

        val msg = createPlainMessage()
        variant.serializeTo(msg)
        msg.seal()
        val variant2 = Variant()
        variant2.deserializeFrom(msg)

        assertEquals(value, variant2.get<TypesComplexType>())
    }

    @Test
    fun anObjectPath_CanBeConstructedFromCString() {
        val aPath = "/some/path"

        assertEquals(aPath, ObjectPath(aPath).toString())
    }

    @Test
    fun aSignature_CanBeConstructedFromCString() {
        val aSignature = "us"

        assertEquals(aSignature, Signature(aSignature).toString())
    }

    @Test
    fun aUnixFd_DuplicatesAndOwnsFdUponStandardConstruction() {
        val fd = eventfd(0, EFD_SEMAPHORE or EFD_NONBLOCK)

        assertTrue(UnixFd(fd).fd > fd)
        assertEquals(0, close(fd))
    }

    @Test
    fun aUnixFd_AdoptsAndOwnsFdAsIsUponAdoptionConstruction() {
        val fd = eventfd(0, EFD_SEMAPHORE or EFD_NONBLOCK)

        val unixFd = UnixFd(fd, adoptFd = Unit)
        assertEquals(fd, unixFd.fd)
        unixFd.release()
        assertEquals(-1, close(fd))
    }

    @Test
    fun aUnixFd_DuplicatesFdUponCopyConstruction() {
        val unixFd = UnixFd(eventfd(0, EFD_SEMAPHORE or EFD_NONBLOCK))

        val unixFdCopy = UnixFd(unixFd)

        assertTrue(unixFdCopy.fd > unixFd.fd)
    }

    @Test
    fun aUnixFd_ClosesFdProperlyUponDestruction() {
        val fd = eventfd(0, EFD_SEMAPHORE or EFD_NONBLOCK)
        val unixFd = UnixFd(fd, adoptFd = Unit)
        val unixFdCopy = UnixFd(unixFd)
        val fdCopy = unixFdCopy.fd
        unixFd.release()
        unixFdCopy.release()

        assertEquals(-1, close(fd))
        assertEquals(-1, close(fdCopy))
    }

    @Test
    fun aUnixFd_ClosesFdOnRelease() {
        val fd = eventfd(0, EFD_SEMAPHORE or EFD_NONBLOCK)
        val unixFd = UnixFd(fd, adoptFd = Unit)

        unixFd.release()

        assertFalse(unixFd.isValid)
        assertEquals(-1, close(fd))
    }

    @Test
    fun anError_CanBeConstructedFromANameAndAMessage() {
        val error = Error("org.sdbuscpp.error", "message")
        assertEquals("org.sdbuscpp.error", error.name)
        assertEquals("message", error.errorMessage)
    }

    @Test
    fun anError_CanBeConstructedFromANameOnly() {
        val error1 = Error("org.sdbuscpp.error")
        val error2 = Error("org.sdbuscpp.error", "")
        assertEquals("org.sdbuscpp.error", error1.name)
        assertEquals("org.sdbuscpp.error", error2.name)

        assertEquals("", error1.errorMessage)
        assertEquals("", error2.errorMessage)
    }
}
