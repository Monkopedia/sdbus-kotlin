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
package com.monkopedia.sdbus

import com.monkopedia.sdbus.Access.READWRITE
import com.monkopedia.sdbus.Access.WRITE
import com.monkopedia.sdbus.Direction.OUT
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.SUSPEND
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Builder
import java.util.*

val String.decapitalCamelCase: String
    get() = split("_").mapIndexed { index, s ->
        if (index == 0) {
            s.decapitalized
        } else {
            s.capitalized
        }
    }.joinToString("")

val String.capitalized: String
    get() = replaceFirstChar {
        it.uppercaseChar()
    }
val String.decapitalized: String
    get() = replaceFirstChar {
        it.lowercase(Locale.getDefault())
    }

val String.capitalCamelCase: String
    get() = decapitalCamelCase.capitalized

class InterfaceGenerator : BaseGenerator() {
    override val fileSuffix: String
        get() = ""

    override fun transformXmlToFile(doc: XmlRootNode): List<FileSpec> =
        super.transformXmlToFile(doc) + namingManager.extraFiles

    override fun classBuilder(intf: Interface): Builder =
        TypeSpec.interfaceBuilder(intf.name.simpleName).apply {
            addType(
                TypeSpec.companionObjectBuilder().apply {
                    addProperty(
                        PropertySpec.builder(
                            "INTERFACE_NAME",
                            ClassName("com.monkopedia.sdbus", "InterfaceName")
                        ).apply {
                            initializer("InterfaceName(%S)", intf.name)
                        }.build()
                    )
                }.build()
            )
        }

    override fun constructorBuilder(intf: Interface): FunSpec.Builder? = null

    override fun methodBuilder(intf: Interface, method: Method): FunSpec.Builder =
        FunSpec.builder(method.name.decapitalCamelCase).apply {
            addModifiers(SUSPEND)
            addModifiers(ABSTRACT)
            for ((index, arg) in method.args.filter { it.direction != OUT }.withIndex()) {
                addParameter((arg.name ?: "arg$index").decapitalCamelCase, namingManager[arg])
            }
            val outputs = method.args.filter { it.direction == OUT }
            returns(namingManager[outputs])
        }

    override fun propertyBuilder(intf: Interface, method: Property): PropertySpec.Builder =
        PropertySpec.builder(method.name.decapitalCamelCase, namingManager[method]).apply {
            addModifiers(ABSTRACT)
            if (method.access == WRITE || method.access == READWRITE) {
                mutable(true)
            }
        }

    override fun signalBuilder(intf: Interface, signal: Signal): FunSpec.Builder? = null

    override fun FunSpec.Builder.buildRegistration(intf: Interface) {
        addModifiers(ABSTRACT)
    }
}
