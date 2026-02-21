package com.monkopedia.sdbus

import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XML.ParsedData
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor

internal object TestXmlSupport {
    private val xml = XML {
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

    fun parse(text: String): XmlRootNode = xml.decodeFromString(text.trimIndent())
}
