package com.monkopedia.sdbus

import com.monkopedia.sdbus.NamingManager.GeneratedType
import com.monkopedia.sdbus.NamingManager.NamingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun proxyGenerationEscapesKotlinKeywordsInCallBody() {
        val xml = """
            <node>
              <interface name="Org.Example.KeywordTest">
                <method name="DoSomething">
                  <arg name="interface" type="s" direction="in"/>
                  <arg name="result" type="b" direction="out"/>
                </method>
              </interface>
            </node>
        """

        val root = TestXmlSupport.parse(xml)
        val proxyFile = ProxyGenerator()
            .transformXmlToFile(root)
            .first { it.name == "KeywordTestProxy" }
            .toString()

        assertTrue(
            proxyFile.contains("call(`interface`)"),
            "Proxy call() should backtick-escape 'interface': $proxyFile"
        )
    }

    @Test
    fun adaptorGenerationEscapesKotlinKeywordsInSignalCallBody() {
        val xml = """
            <node>
              <interface name="Org.Example.KeywordTest">
                <signal name="StatusChanged">
                  <arg name="interface" type="s"/>
                  <arg name="object" type="s"/>
                </signal>
              </interface>
            </node>
        """

        val root = TestXmlSupport.parse(xml)
        val adaptorFile = AdaptorGenerator()
            .transformXmlToFile(root)
            .first { it.name == "KeywordTestAdaptor" }
            .toString()

        assertTrue(
            adaptorFile.contains("call(`interface`, `object`)"),
            "Adaptor signal call() should backtick-escape keywords: $adaptorFile"
        )
    }

    @Test
    fun namingAnnotationsAreInertUnlessHonored() {
        val root = TestXmlSupport.parse(HINTED_XML)

        val default = InterfaceGenerator().transformXmlToFile(root)
            .associate { it.name to it.toString() }

        assertEquals(setOf("Hinted", "Corner"), default.keys)
        assertTrue(default.getValue("Hinted").contains("val corner: Corner"), default.toString())
        assertTrue(
            default.getValue("Hinted").contains("val extra: Map<String, Variant>"),
            default.toString()
        )
    }

    @Test
    fun honoredNamingAnnotationsRenameStructsAndAliasEverythingElse() {
        val root = TestXmlSupport.parse(HINTED_XML)

        val hinted = InterfaceGenerator(honorNamingAnnotations = true)
            .transformXmlToFile(root)
            .associate { it.name to it.toString() }

        assertEquals(setOf("Hinted", "QPoint", "QVariantMap"), hinted.keys)
        // The struct had a generated class already, so the hint renamed it in place.
        assertTrue(hinted.getValue("QPoint").contains("data class QPoint"), hinted.toString())
        // a{sv} generates nothing, so the hint is a typealias and stays a Map at the call site.
        assertTrue(
            hinted.getValue("QVariantMap")
                .contains("typealias QVariantMap = Map<String, Variant>"),
            hinted.toString()
        )
        assertTrue(hinted.getValue("Hinted").contains("val corner: QPoint"), hinted.toString())
        assertTrue(hinted.getValue("Hinted").contains("val extra: QVariantMap"), hinted.toString())
    }

    @Test
    fun namingAnnotationThatIsNotAKotlinNameFailsLoudly() {
        val root = TestXmlSupport.parse(
            """
            <node>
              <interface name="org.example.Hinted">
                <property name="Corner" type="(ii)" access="read">
                  <annotation name="org.qtproject.QtDBus.QtTypeName"
                              value="QMap&lt;QString,QVariant&gt;"/>
                </property>
              </interface>
            </node>
            """
        )

        val message = assertFailsWith<IllegalArgumentException> {
            InterfaceGenerator(honorNamingAnnotations = true).transformXmlToFile(root)
        }.message

        assertTrue(message.orEmpty().contains("QMap<QString,QVariant>"), message)
    }

    private companion object {
        private val HINTED_XML = """
            <node>
              <interface name="org.example.Hinted">
                <property name="Corner" type="(ii)" access="read">
                  <annotation name="org.qtproject.QtDBus.QtTypeName" value="QPoint"/>
                </property>
                <property name="Extra" type="a{sv}" access="read">
                  <annotation name="org.qtproject.QtDBus.QtTypeName" value="QVariantMap"/>
                </property>
              </interface>
            </node>
        """
    }
}
