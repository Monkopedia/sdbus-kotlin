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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XML.ParsedData
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor

/**
 * The serialization format the [Xml2Kotlin] CLI and the tests share, so the golden fixtures assert
 * against the configuration the tool actually ships. Unknown content is dropped rather than
 * failing, so vendor extensions don't break generation. Call [parseIntrospectionXml] rather than
 * this directly — it is what applies the limits hostile XML is held to.
 *
 * `XML.compat` is deprecated in xmlutil 1.0, but its replacements deliberately change the parsing
 * defaults and xmlutil offers no non-deprecated route back to the old ones. Switching is a
 * behaviour change needing its own review, so the compat defaults stay pinned here.
 */
@OptIn(ExperimentalXmlUtilApi::class)
@Suppress("DEPRECATION")
internal val introspectionXml: XML = XML.compat {
    isUnchecked = true
    policy = object : DefaultXmlSerializationPolicy(policy) {
        override fun handleUnknownContentRecovering(
            input: XmlReader,
            inputKind: InputKind,
            descriptor: XmlDescriptor,
            name: QName?,
            candidates: Collection<Any>
        ): List<ParsedData<*>> = emptyList()
    }
}

/**
 * Parses D-Bus introspection XML into the model the generators consume. The one entry point the CLI
 * and the tests both use, so what the golden fixtures assert against and what the tool ships are
 * held to the same limit.
 *
 * Introspection XML is supplied by the service being introspected, and `:codegen` runs inside a
 * developer's build (the Gradle plugin calls it in-process), so a hostile document costs build
 * availability rather than anything at runtime.
 */
internal fun parseIntrospectionXml(xml: String): XmlRootNode {
    prologRefusal(xml)?.let { throw IllegalArgumentException(it) }
    return introspectionXml.decodeFromString(xml)
}

/**
 * Why [xml]'s prolog must not be handed to the parser, or null to go ahead.
 *
 * A `<!DOCTYPE ... [ ... ]>` internal subset is the only place introspection XML can declare XML
 * entities, and xmlutil's parser expands them with no limit of any kind: measured against xmlutil
 * 1.0.1, a 421-byte document nesting six ten-fold entities decodes to a 1,000,000-character
 * attribute value in 30ms — each further level multiplies by ten — and `<!ENTITY a "&a;">` never
 * terminates at all. There is no configuration knob for that (the JVM path is xmlutil's own
 * `KtXmlReader`, not JAXP, so the `jdk.xml.entityExpansionLimit` default does not apply), and
 * `expandEntities = false` would not help either because attribute values are expanded
 * unconditionally.
 *
 * Refusing the subset outright is both the complete fix and the narrowest one: it is the only route
 * to a declared entity, external entities the parser already rejects on its own, and what a real
 * service emits is at most the external `introspect.dtd` doctype, which still parses.
 *
 * Only the prolog is read — the byte order mark, XML declaration, comments and processing
 * instructions that may precede the doctype are stepped over — so a literal `<!DOCTYPE` in a
 * comment or in element content is not mistaken for a declaration. **A prolog this cannot read is
 * refused rather than assumed to declare nothing**: the scan is what decides whether entities are
 * possible, so treating what it does not model as safe is how a leading byte order mark got past an
 * earlier version of it. The only thing that legitimately ends the scan is the root element, which
 * is a `<` opening neither a markup declaration nor a processing instruction.
 */
private fun prologRefusal(xml: String): String? {
    var i = if (xml.startsWith(BYTE_ORDER_MARK)) 1 else 0
    while (i < xml.length) {
        when {
            xml[i].isWhitespace() -> i++
            xml.startsWith("<!--", i) -> i = xml.endOf("-->", i + 4) ?: return UNREADABLE_PROLOG
            xml.startsWith("<?", i) -> i = xml.endOf("?>", i + 2) ?: return UNREADABLE_PROLOG
            xml.startsWith("<!DOCTYPE", i) -> return doctypeRefusal(xml, i + "<!DOCTYPE".length)
            xml[i] == '<' && !xml.startsWith("<!", i) -> return null // The root element.
            else -> return UNREADABLE_PROLOG
        }
    }
    return UNREADABLE_PROLOG
}

/** [prologRefusal] for the doctype declaration running from [start] in [xml]. */
private fun doctypeRefusal(xml: String, start: Int): String? {
    var i = start
    while (i < xml.length) {
        when (val c = xml[i]) {
            '[' -> return INTERNAL_SUBSET
            '>' -> return null
            // A quoted public or system literal; '[' and '>' inside it mean neither of the above.
            '\'', '"' -> i = xml.endOf(c.toString(), i + 1) ?: return UNREADABLE_PROLOG
            else -> i++
        }
    }
    return UNREADABLE_PROLOG
}

/** The index just past the next [token] at or after [from], or null if there isn't one. */
private fun String.endOf(token: String, from: Int): Int? =
    indexOf(token, from).takeIf { it >= 0 }?.plus(token.length)

private const val BYTE_ORDER_MARK = '\uFEFF'

private const val INTERNAL_SUBSET =
    "Introspection XML declares a DTD internal subset (<!DOCTYPE ... [ ... ]>). Introspection " +
        "XML has no use for one, and the entities it can declare are expanded without any limit, " +
        "so the declaration is refused rather than parsed."

private const val UNREADABLE_PROLOG =
    "The XML prolog of the introspection XML could not be read as far as the root element, so " +
        "whether it may declare entities is unknown. It is refused rather than guessed at; the " +
        "parser would reject it in any case."

@Serializable
@XmlSerialName("node")
data class XmlRootNode(
    val name: String? = null,
    val interfaces: List<Interface>,
    @SerialName("node")
    val nodes: List<XmlRootNode>,
    val doc: Doc? = null
)

@Serializable
@XmlSerialName("interface")
data class Interface(
    val name: String,
    @SerialName("method")
    val methods: List<Method>,
    @SerialName("signal")
    val signals: List<Signal>,
    @SerialName("property")
    val properties: List<Property>,
    @SerialName("annotation")
    val annotations: List<Annotation>
)

@Serializable
@XmlSerialName("doc", "http://www.freedesktop.org/dbus/1.0/doc.dtd")
data class Doc(val summary: Summary? = null, val description: Description? = null)

@Serializable
@XmlSerialName("summary", "http://www.freedesktop.org/dbus/1.0/doc.dtd")
data class Summary(
    @XmlValue
    val summaryText: String? = null
)

@Serializable
@XmlSerialName("description", "http://www.freedesktop.org/dbus/1.0/doc.dtd")
data class Description(val para: Para? = null)

@Serializable
@XmlSerialName("para", "http://www.freedesktop.org/dbus/1.0/doc.dtd")
data class Para(
    @XmlValue
    val paraContent: String? = null
)

@Serializable
@XmlSerialName("method")
data class Method(
    val name: String,
    val args: List<Arg>,
    @SerialName("annotation")
    val annotations: List<Annotation>,
    val doc: Doc? = null
)

@Serializable
@XmlSerialName("signal")
data class Signal(
    val name: String,
    val args: List<Arg>,
    @SerialName("annotation")
    val annotations: List<Annotation>,
    val doc: Doc? = null
)

@Serializable
@XmlSerialName("direction")
enum class Direction {
    @SerialName("in")
    IN,

    @SerialName("out")
    OUT
}

@Serializable
@XmlSerialName("arg")
data class Arg(
    val name: String? = null,
    val type: String,
    @XmlElement(false)
    val direction: Direction? = null,
    val annotations: List<Annotation> = emptyList(),
    val doc: Doc? = null
)

@Serializable
enum class Access {
    @SerialName("readwrite")
    READWRITE,

    @SerialName("write")
    WRITE,

    @SerialName("read")
    READ
}

@Serializable
@XmlSerialName("property")
data class Property(
    val name: String,
    val type: String,
    @XmlElement(false)
    val access: Access,
    val annotations: List<Annotation>,
    val doc: Doc? = null
)

@Serializable
@XmlSerialName("annotation")
data class Annotation(val name: String, val value: String, val doc: Doc? = null)

/** Names of the standard D-Bus annotations the generators act on. See the table below. */
internal object Annotations {
    const val DEPRECATED = "org.freedesktop.DBus.Deprecated"
    const val METHOD_NO_REPLY = "org.freedesktop.DBus.Method.NoReply"
    const val EMITS_CHANGED_SIGNAL = "org.freedesktop.DBus.Property.EmitsChangedSignal"
}

/**
 * Whether the boolean annotation [name] is present on [this] and set. Both `Deprecated` and
 * `Method.NoReply` default to `false` when absent, so anything other than `true` means unset.
 */
internal fun List<Annotation>.isSet(name: String): Boolean =
    firstOrNull { it.name == name }?.value == "true"

/**
 * Name	Values (separated by ,)	Description
 * org.freedesktop.DBus.Deprecated	true,false	Whether or not the entity is deprecated; defaults to false
 * org.freedesktop.DBus.GLib.CSymbol	(string)	The C symbol; may be used for methods and interfaces
 * org.freedesktop.DBus.Method.NoReply	true,false	If set, don't expect a reply to the method call; defaults to false.
 * org.freedesktop.DBus.Property.EmitsChangedSignal	true,invalidates,const,false
 * If set to false, the org.freedesktop.DBus.Properties.PropertiesChanged signal, see the section called “org.freedesktop.DBus.Properties” is not guaranteed to be emitted if the property changes.
 *
 * If set to const the property never changes value during the lifetime of the object it belongs to, and hence the signal is never emitted for it.
 *
 * If set to invalidates the signal is emitted but the value is not included in the signal.
 *
 * If set to true the signal is emitted with the value included.
 *
 * The value for the annotation defaults to true if the enclosing interface element does not specify the annotation. Otherwise it defaults to the value specified in the enclosing interface element.
 *
 * This annotation is intended to be used by code generators to implement client-side caching of property values. For all properties for which the annotation is set to const, invalidates or true the client may unconditionally cache the values as the properties don't change or notifications are generated for them if they do.
 *
 *
 */
