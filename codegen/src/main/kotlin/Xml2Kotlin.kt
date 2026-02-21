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
/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
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
/**
 *
 * (C) 2016 - 2021 KISTLER INSTRUMENTE AG, Winterthur, Switzerland
 * (C) 2016 - 2024 Stanislav Angelovic <stanislav.angelovic@protonmail.com>
 * (C) 2024 - 2024 Jason Monk <monkopedia@gmail.com>
 *
 * Project: sdbus-kotlin
 * Description: High-level D-Bus IPC kotlin library based on sd-bus
 *
 * This file is part of sdbus-kotlin.
 *
 * sdbus-kotlin is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * sdbus-kotlin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sdbus-kotlin. If not, see <http://www.gnu.org/licenses/>.
 */
@file:OptIn(ExperimentalSerializationApi::class)

package com.monkopedia.sdbus

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XML.ParsedData
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor

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
    val outputPackage by option(
        "--output-package",
        help = "Override Kotlin package for generated files"
    )

    override fun run() {
        val packageOverride = outputPackage?.trim()?.takeUnless { it.isEmpty() }
        val xml = XML {
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
        }.decodeFromString<XmlRootNode>(input.readText())
        if (!keep) {
            output.deleteRecursively()
        }
        output.mkdirs()
        InterfaceGenerator(packageOverride).transformXmlToFile(xml).forEach {
            it.writeTo(output)
        }
        if (adaptor) {
            AdaptorGenerator(packageOverride).transformXmlToFile(xml).forEach {
                it.writeTo(output)
            }
        }
        if (proxy) {
            ProxyGenerator(packageOverride).transformXmlToFile(xml).forEach {
                it.writeTo(output)
            }
        }
    }
}
