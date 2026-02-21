package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.SerializationException

class CodegenParsingEdgeCaseTest {
    @Test
    fun parserRejectsMethodMissingRequiredName() {
        val invalidXml = """
            <node>
              <interface name="org.example.Bad">
                <method>
                  <arg type="s" direction="out"/>
                </method>
              </interface>
            </node>
        """

        assertFailsWith<SerializationException> {
            TestXmlSupport.parse(invalidXml)
        }
    }

    @Test
    fun parserPreservesUnknownAnnotationEntries() {
        val xml = """
            <node>
              <interface name="org.example.Annotation">
                <method name="Ping">
                  <annotation name="com.example.Unknown" value="true"/>
                </method>
              </interface>
            </node>
        """

        val root = TestXmlSupport.parse(xml)
        val annotation = root.interfaces.single().methods.single().annotations.single()
        assertEquals("com.example.Unknown", annotation.name)
        assertEquals("true", annotation.value)
    }

    @Test
    fun parserIgnoresUnknownXmlElements() {
        val xml = """
            <node>
              <interface name="org.example.UnknownContent">
                <method name="Ping">
                  <arg type="s" direction="out"/>
                  <something-else attr="ignored"/>
                </method>
              </interface>
            </node>
        """

        val root = TestXmlSupport.parse(xml)
        assertEquals(1, root.interfaces.single().methods.single().args.size)
        assertEquals("s", root.interfaces.single().methods.single().args.single().type)
    }

    @Test
    fun namingManagerRejectsMalformedMapSignature() {
        val xml = """
            <node>
              <interface name="org.example.Malformed">
                <method name="Broken">
                  <arg type="a{is" direction="out"/>
                </method>
              </interface>
            </node>
        """

        val root = TestXmlSupport.parse(xml)
        val failure = assertFailsWith<IllegalArgumentException> {
            NamingManager(root)
        }
        assertTrue(failure.message.orEmpty().contains("Map did not close after value"))
    }
}
