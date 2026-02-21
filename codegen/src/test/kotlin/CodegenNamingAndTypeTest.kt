package com.monkopedia.sdbus

import com.monkopedia.sdbus.NamingManager.GeneratedType
import com.monkopedia.sdbus.NamingManager.NamingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CodegenNamingAndTypeTest {
    @Test
    fun buildTypeMapsPrimitivesCollectionsVariantsAndUnixFd() {
        val types = mutableMapOf<String, NamingType>()

        assertEquals("kotlin.Int", types.buildType("org.example", "i", "i").reference.toString())
        assertEquals("kotlin.UInt", types.buildType("org.example", "u", "u").reference.toString())
        assertEquals(
            "com.monkopedia.sdbus.UnixFd",
            types.buildType("org.example", "fd", "h").reference.toString()
        )
        assertEquals(
            "com.monkopedia.sdbus.Variant",
            types.buildType("org.example", "variant", "v").reference.toString()
        )
        assertEquals(
            "kotlin.collections.List<kotlin.Int>",
            types.buildType("org.example", "list", "ai").reference.toString()
        )
        assertEquals(
            "kotlin.collections.Map<kotlin.String, com.monkopedia.sdbus.Variant>",
            types.buildType("org.example", "dict", "a{sv}").reference.toString()
        )
        assertEquals(
            "kotlin.collections.Map<kotlin.String, kotlin.collections.Map<kotlin.Int, com.monkopedia.sdbus.Variant>>",
            types.buildType("org.example", "nested", "a{sa{iv}}").reference.toString()
        )
    }

    @Test
    fun buildTypeReusesStructSignaturesDeterministically() {
        val types = mutableMapOf<String, NamingType>()

        val first = types.buildType("org.example", "tupleResult", "(is)")
        val second = types.buildType("org.example", "tupleResultAgain", "(is)")

        assertTrue(first is GeneratedType)
        assertSame(first, second)
        first as GeneratedType
        assertEquals("(is)", first.type)
        assertTrue(first.nameReferences.contains("tupleResult"))
        assertTrue(first.nameReferences.contains("tupleResultAgain"))
    }

    @Test
    fun interfaceGenerationAppliesNameManglingAndIsDeterministic() {
        val xml = """
            <node>
              <interface name="Org.Example.CasingTest">
                <method name="set_value">
                  <arg name="result" type="b" direction="out"/>
                  <arg name="class" type="s" direction="in"/>
                </method>
                <method name="duplicate_values">
                  <arg name="value" type="s" direction="out"/>
                  <arg name="value" type="u" direction="out"/>
                </method>
                <property name="snake_case" type="s" access="readwrite"/>
              </interface>
            </node>
        """

        val root = TestXmlSupport.parse(xml)
        val firstRun = InterfaceGenerator()
            .transformXmlToFile(root)
            .associate { it.name to it.toString() }
        val secondRun = InterfaceGenerator()
            .transformXmlToFile(root)
            .associate { it.name to it.toString() }
        assertEquals(firstRun, secondRun)

        val interfaceFile = firstRun["CasingTest"]
        assertNotNull(interfaceFile)
        assertTrue(interfaceFile.contains("package org.example"))
        assertTrue(interfaceFile.contains("suspend fun setValue(`class`: String): Boolean"))
        assertTrue(interfaceFile.contains("var snakeCase: String"))

        val generatedTupleType = firstRun.entries
            .firstOrNull { (name, _) -> name != "CasingTest" }
            ?.value
        assertNotNull(generatedTupleType)
        assertTrue(generatedTupleType.contains("data class"))
        assertTrue(generatedTupleType.contains("val `value`: String"), generatedTupleType)
        assertTrue(generatedTupleType.contains("val value1: UInt"), generatedTupleType)
    }
}
