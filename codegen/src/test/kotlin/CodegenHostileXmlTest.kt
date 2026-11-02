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
package com.monkopedia.sdbus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The limits [parseIntrospectionXml] and [buildType] hold introspection XML to.
 *
 * Introspection XML comes from the service being introspected and `:codegen` runs inside a
 * developer's build, so an input that makes the parser do unbounded work costs build availability.
 * Each case below pairs the input that used to be unbounded with a positive control that the
 * limit does not touch, so the suite discriminates the defect rather than the input shape.
 *
 * Every parse here runs under [parseWithin]: the point of these limits is that the answer arrives
 * promptly, so a regression has to fail the suite rather than hang it.
 */
class CodegenHostileXmlTest {

    @Test
    fun entityDeclarationThatExpandsTenFoldPerLevel_isRefusedRatherThanExpanded() {
        // Four levels expand to 10,000 characters, which the parser produced in 2ms before the
        // limit; the shape is what matters, not the size. Levels are a multiplier, so the same
        // document with three more of them is 10MB and with a few more it exhausts the heap of
        // whatever build ran it.
        assertRefusedAsInternalSubset(TEN_FOLD_ENTITIES_FOUR_LEVELS)
    }

    @Test
    fun entityThatReferencesItself_isRefusedRatherThanNeverTerminating() {
        // Before the limit this document did not fail, and did not succeed either: the parser
        // re-injected the entity into its own replacement text indefinitely.
        assertRefusedAsInternalSubset(SELF_REFERENTIAL_ENTITY)
    }

    /**
     * A byte order mark is what `File.readText()` leaves at the front of a UTF-8 document that has
     * one, and the parser accepts it, so the prolog has to be read past it rather than given up on.
     * Both entity cases get their own case behind one, since they fail differently without it: this
     * one parses, and the self-referential one below does not come back at all.
     */
    @Test
    fun entityDeclarationBehindAByteOrderMark_isStillRefused() {
        assertRefusedAsInternalSubset(BOM + TEN_FOLD_ENTITIES_FOUR_LEVELS)
    }

    @Test
    fun selfReferentialEntityBehindAByteOrderMark_isStillRefused() {
        assertRefusedAsInternalSubset(BOM + SELF_REFERENTIAL_ENTITY)
    }

    /**
     * A second `<!DOCTYPE` after the one a real service sends. Two of them is not well-formed XML,
     * but the parser honours the second and declares its entities, so the scan has to keep reading
     * to the root element rather than stopping at the first declaration's `>`.
     */
    @Test
    fun secondDoctypeBehindARealIntrospectReplyDoctype_isStillRefused() {
        assertRefusedAsInternalSubset(
            REAL_INTROSPECT_DOCTYPE + "\n" + TEN_FOLD_ENTITIES_FOUR_LEVELS
        )
    }

    @Test
    fun selfReferentialEntityInASecondDoctype_isStillRefused() {
        assertRefusedAsInternalSubset(REAL_INTROSPECT_DOCTYPE + "\n" + SELF_REFERENTIAL_ENTITY)
    }

    /**
     * The scan resumes after a doctype rather than after the next declaration in particular, so a
     * comment or processing instruction between the two does not hide the second one either.
     */
    @Test
    fun secondDoctypeBehindACommentAndAProcessingInstruction_isStillRefused() {
        assertRefusedAsInternalSubset(
            "<!DOCTYPE node>\n<!-- a comment -->\n<?vendor data?>\n" + TEN_FOLD_ENTITIES_FOUR_LEVELS
        )
    }

    /**
     * Why the root element may end the scan when the first doctype's `>` may not: the parser does
     * not apply a declaration that follows the root element, so there is nothing left to refuse
     * once the root is reached. This is the parser behaviour [parseIntrospectionXml] relies on.
     */
    @Test
    fun entityDeclaredAfterTheRootElement_isNotHonouredByTheParser() {
        val failure = parseWithin(
            """
            <node>
              <interface name="org.example.Late">
                <method name="Get">
                  <annotation name="org.gtk.GDBus.DocString" value="&a;"/>
                </method>
              </interface>
            </node>
            <!DOCTYPE node [ <!ENTITY a "&a;"> ]>
            """
        ) ?: fail("expected the entity reference to fail, but the document parsed")
        assertTrue(
            failure.message.orEmpty().contains("Unknown entity"),
            "expected the parser not to honour the late declaration, got: ${failure.message}"
        )
    }

    @Test
    fun documentBehindAByteOrderMark_stillParses() {
        val xml = BOM + """
            <node>
              <interface name="org.example.Marked">
                <method name="Ping"/>
              </interface>
            </node>
        """.trimIndent()

        assertEquals(null, parseWithin(xml))
        assertEquals("org.example.Marked", TestXmlSupport.parse(xml).interfaces.single().name)
    }

    /**
     * The prolog scan decides whether a document may declare entities, so what it does with a
     * prolog it cannot read is the whole question: assuming such a document safe is how the byte
     * order mark got past it. Each shape below is one the scan does not model, and none of them may
     * be waved through on the grounds that no `<!DOCTYPE` was recognised.
     */
    @Test
    fun prologTheScanCannotRead_isRefusedRatherThanAssumedToDeclareNothing() {
        val unreadablePrologs = mapOf(
            "text before the root element" to "stray text ",
            "a space inside the declaration name" to """<! DOCTYPE node [ <!ENTITY a "x"> ]>""",
            "a lower-case declaration name" to """<!doctype node [ <!ENTITY a "x"> ]>""",
            "a CDATA section, which a prolog cannot contain" to "<![CDATA[ <!DOCTYPE node [ ]]>",
            "a second byte order mark" to BOM + BOM,
            "the bytes of a UTF-16 byte order mark read as UTF-8" to "\uFFFE"
        )

        for ((shape, prolog) in unreadablePrologs) {
            val failure = parseWithin(prolog + "<node/>")
                ?: fail("expected $shape to be refused, but the document parsed")
            assertTrue(
                failure.message.orEmpty().contains("prolog"),
                "expected $shape to say the prolog could not be read, got: ${failure.message}"
            )
        }
    }

    /** The doctype a real `dbus-daemon` puts on every `Introspect` reply. */
    @Test
    fun externalDoctypeOfARealIntrospectReply_stillParses() {
        val xml = REAL_INTROSPECT_DOCTYPE + "\n" + """
            <?vendor data?>
            <!-- a comment mentioning <!DOCTYPE node [ to prove only the prolog is scanned -->
            <node>
              <interface name="org.example.Real">
                <method name="Ping"/>
              </interface>
            </node>
        """.trimIndent()

        assertEquals(null, parseWithin(xml))
        assertEquals("org.example.Real", TestXmlSupport.parse(xml).interfaces.single().name)
    }

    @Test
    fun signatureLongerThanTheSpecMaximum_isRefusedRatherThanOverflowingTheStack() {
        // 5000 array type codes overflowed the stack in NamingManager.buildType before the limit;
        // 255 is the specification's own maximum signature length.
        val failure = namingManagerFailureFor("a".repeat(5000) + "s")
            ?: fail("expected the signature to be refused, but it was accepted")
        assertTrue(
            failure.message.orEmpty().contains("over the D-Bus maximum of 255"),
            "expected the failure to name the limit it hit, got: " +
                "${failure::class.simpleName}: ${failure.message}"
        )
    }

    @Test
    fun signatureAtTheSpecMaximum_stillParses() {
        // 254 array type codes around an 's' is 255 characters, so it is legal and still resolves
        // through 255 levels of recursion. This passed before the limit too — it is the control
        // that keeps the case above from being satisfied by refusing deep signatures generally.
        assertEquals(null, namingManagerFailureFor("a".repeat(254) + "s"))
    }

    /**
     * The shape #259 measured: `<node>` nests without limit and the decoder recurses per level, so
     * 60,000 bytes with no entity declaration in them aborted the parse with `StackOverflowError`.
     * The document is bounded because the refusal has to arrive before the decoder is ever handed
     * it — the scan stops at the first element over the limit rather than reading to the end.
     */
    @Test
    fun nestingDeeperThanTheLimit_isRefusedRatherThanOverflowingTheStack() {
        assertRefusedAsTooDeeplyNested(nestedNodes(5_000))
    }

    /**
     * One level over, to pin where the limit actually falls. The depth the decoder overflows at is
     * not a fixed one — measured on JDK 21 at a 256KB stack, 68 levels cold and 545 warm — so what
     * is pinned here is the limit, and the refusal at it is a clean message rather than an `Error`.
     */
    @Test
    fun nestingOneLevelOverTheLimit_isRefused() {
        assertRefusedAsTooDeeplyNested(nestedNodes(MAX_NESTING_DEPTH + 1))
    }

    /**
     * The control that keeps the two cases above from being satisfied by refusing nesting
     * generally: a document nested to exactly the limit still parses, all the way down. A real
     * `Introspect` reply is `node > interface > method > arg` and the deepest of the 23 checked-in
     * fixtures is 6 elements, so the limit sits an order of magnitude above anything legitimate.
     */
    @Test
    fun nestingAtTheLimit_stillParses() {
        val xml = nestedNodes(MAX_NESTING_DEPTH)

        assertEquals(null, parseWithin(xml))
        var node = TestXmlSupport.parse(xml)
        repeat(MAX_NESTING_DEPTH - 2) { node = node.nodes.single() }
        assertEquals("org.example.Deep", node.interfaces.single().name)
    }

    /** A document whose deepest element — the interface at the bottom — sits at [depth]. */
    private fun nestedNodes(depth: Int): String = "<node>".repeat(depth - 1) +
        """<interface name="org.example.Deep"/>""" +
        "</node>".repeat(depth - 1)

    private fun assertRefusedAsTooDeeplyNested(xml: String) {
        val failure = parseWithin(xml)
            ?: fail("expected the nesting to be refused, but the document parsed")
        assertTrue(
            failure.message.orEmpty().contains("maximum nesting depth of $MAX_NESTING_DEPTH"),
            "expected the failure to name the limit it hit, got: " +
                "${failure::class.simpleName}: ${failure.message}"
        )
    }

    private fun assertRefusedAsInternalSubset(xml: String) {
        val failure = parseWithin(xml)
            ?: fail("expected the entity declaration to be refused, but the document parsed")
        assertTrue(
            failure.message.orEmpty().contains("DTD internal subset"),
            "expected the failure to name the declaration it refused, got: ${failure.message}"
        )
    }

    private fun namingManagerFailureFor(signature: String): Throwable? = runCatching {
        NamingManager(
            TestXmlSupport.parse(
                """
                <node>
                  <interface name="org.example.Signature">
                    <method name="Get">
                      <arg name="value" type="$signature" direction="out"/>
                    </method>
                  </interface>
                </node>
                """
            )
        )
    }.exceptionOrNull()

    /**
     * Parses [xml] and returns what it failed with, or null if it parsed. Fails the test if the
     * parse has not finished within [PARSE_TIMEOUT_MS] — an unbounded parse is exactly the defect
     * under test, and a test that hangs takes out the job meant to catch it.
     */
    private fun parseWithin(xml: String, timeoutMs: Long = PARSE_TIMEOUT_MS): Throwable? {
        var failure: Throwable? = null
        val parse = Thread { failure = runCatching { TestXmlSupport.parse(xml) }.exceptionOrNull() }
        parse.isDaemon = true
        parse.start()
        parse.join(timeoutMs)
        if (parse.isAlive) fail("parse had not finished after ${timeoutMs}ms")
        return failure
    }

    companion object {
        /**
         * The parser's limit, spelled out here rather than shared with it: the boundary pair below
         * is what says where it falls, so moving it has to mean changing this too.
         */
        private const val MAX_NESTING_DEPTH = 64

        /** Two orders of magnitude over the green path, which answers in under a hundredth. */
        private const val PARSE_TIMEOUT_MS = 5_000L

        /** Spelled out rather than written literally, since the character itself is invisible. */
        private const val BOM = "\uFEFF"

        /** What a real `dbus-daemon` puts in front of every `Introspect` reply, and nothing else. */
        private val REAL_INTROSPECT_DOCTYPE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE node PUBLIC "-//freedesktop//DTD D-BUS Object Introspection 1.0//EN"
             "http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd">
        """.trimIndent()

        private val TEN_FOLD_ENTITIES_FOUR_LEVELS = """
            <!DOCTYPE node [
              <!ENTITY a "aaaaaaaaaa">
              <!ENTITY b "&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;">
              <!ENTITY c "&b;&b;&b;&b;&b;&b;&b;&b;&b;&b;">
              <!ENTITY d "&c;&c;&c;&c;&c;&c;&c;&c;&c;&c;">
            ]>
            <node>
              <interface name="org.example.Expanding">
                <method name="Get">
                  <annotation name="org.gtk.GDBus.DocString" value="&d;"/>
                </method>
              </interface>
            </node>
        """.trimIndent()

        private val SELF_REFERENTIAL_ENTITY = """
            <!DOCTYPE node [ <!ENTITY a "&a;"> ]>
            <node>
              <interface name="org.example.Recursive">
                <method name="Get">
                  <annotation name="org.gtk.GDBus.DocString" value="&a;"/>
                </method>
              </interface>
            </node>
        """.trimIndent()
    }
}
