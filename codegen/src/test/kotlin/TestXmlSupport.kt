package com.monkopedia.sdbus

import kotlinx.serialization.decodeFromString

internal object TestXmlSupport {
    fun parse(text: String): XmlRootNode = introspectionXml.decodeFromString(text.trimIndent())
}
