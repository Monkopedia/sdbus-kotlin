@file:OptIn(ExperimentalSerializationApi::class)

package com.monkopedia.sdbus

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML

fun main(args: Array<String>) = Xml2Kotlin().main(args)

class Xml2Kotlin : CliktCommand() {
    val input by argument().file(mustExist = true, canBeDir = false, mustBeReadable = true)
    val output by option("-o", "--output", help = "Output directory").file(
        mustExist = false,
        canBeFile = false
    ).default(File("out"))
    val keep by option("-k", "--keep", help = "Do not delete existing content in output")
        .flag()
    val adaptor by option("-a", "--adaptor", help = "Generate the code for an adaptor")
        .flag()
    val proxy by option("-p", "--proxy", help = "Generate the code for a proxy")
        .flag()

    override fun run() {
        val xml = XML.decodeFromString<XmlRootNode>(input.readText())
        if (!keep) {
            output.deleteRecursively()
        }
        output.mkdirs()
        InterfaceGenerator().transformXmlToFile(xml).forEach {
            it.writeTo(output)
        }
        if (adaptor) {
            AdaptorGenerator().transformXmlToFile(xml).forEach {
                it.writeTo(output)
            }
        }
        if (proxy) {
            ProxyGenerator().transformXmlToFile(xml).forEach {
                it.writeTo(output)
            }
        }
    }
}
