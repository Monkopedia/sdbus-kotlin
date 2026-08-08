package com.monkopedia.sdbus

internal object TestXmlSupport {
    fun parse(text: String): XmlRootNode = parseIntrospectionXml(text.trimIndent())
}
