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
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

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
