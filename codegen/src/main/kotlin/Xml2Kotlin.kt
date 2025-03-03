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
import com.monkopedia.sdbus.com.monkopedia.sdbus.GenerationConfig
import java.io.File
import kotlinx.serialization.ExperimentalSerializationApi
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
    val propertyFlows by option(
        "-f",
        "--property-flow",
        help = "Generate properties as Flow types, " +
            "assuming the object implements org.freedesktop.DBus.Properties"
    ).flag()

    override fun run() {
        val generationConfig = GenerationConfig(
            generateAdaptors = adaptor,
            generateProxies = proxy,
            usePropertyFlows = propertyFlows
        )
        val xml = XML.decodeFromString<XmlRootNode>(input.readText())
        if (!keep) {
            output.deleteRecursively()
        }
        output.mkdirs()
        InterfaceGenerator(generationConfig).transformXmlToFile(xml).forEach {
            it.writeTo(output)
        }
        if (adaptor) {
            AdaptorGenerator(generationConfig).transformXmlToFile(xml).forEach {
                it.writeTo(output)
            }
        }
        if (proxy) {
            ProxyGenerator(generationConfig).transformXmlToFile(xml).forEach {
                it.writeTo(output)
            }
        }
    }
}
