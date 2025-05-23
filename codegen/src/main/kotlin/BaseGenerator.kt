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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import java.util.*

abstract class BaseGenerator {

    protected lateinit var namingManager: NamingManager
    protected abstract val fileSuffix: String
    protected val String.simpleName: String
        get() {
            return split(".").last()
        }

    open fun transformXmlToFile(doc: XmlRootNode): List<FileSpec> {
        namingManager = NamingManager(doc)
        return fileSpecs(doc) + doc.nodes.flatMap { fileSpecs(it) }
    }

    private fun fileSpecs(doc: XmlRootNode) = doc.interfaces.map {
        generateFile(it)
    }

    private fun generateFile(intf: Interface): FileSpec =
        FileSpec.builder(intf.name.pkg, intf.name.simpleName + fileSuffix)
            .buildInterface(intf)
            .build()

    private fun FileSpec.Builder.buildInterface(intf: Interface): FileSpec.Builder = apply {
        addType(
            classBuilder(intf).apply {
                constructorBuilder(intf)?.let { primaryConstructor(it.build()) }

                addFunction(
                    FunSpec.builder("register").also {
                        it.buildRegistration(intf)
                    }.build()
                )
                for (method in intf.methods) {
                    methodBuilder(intf, method)?.build()?.let { addFunction(it) }
                }
                for (property in intf.properties) {
                    extraPropertyBuilder(intf, property)?.build()?.let { addProperty(it) }
                }
                for (property in intf.properties) {
                    propertyBuilder(intf, property)?.build()?.let { addProperty(it) }
                }
                for (signal in intf.signals) {
                    signalBuilder(intf, signal)?.build()?.let { addFunction(it) }
                    signalValBuilder(intf, signal)?.build()?.let { addProperty(it) }
                }
            }.build()
        )
    }

    protected abstract fun classBuilder(intf: Interface): TypeSpec.Builder
    protected abstract fun constructorBuilder(intf: Interface): FunSpec.Builder?
    protected abstract fun methodBuilder(intf: Interface, method: Method): FunSpec.Builder?
    protected abstract fun propertyBuilder(intf: Interface, prop: Property): PropertySpec.Builder?
    protected abstract fun signalBuilder(intf: Interface, signal: Signal): FunSpec.Builder?

    protected open fun extraPropertyBuilder(
        intf: Interface,
        prop: Property
    ): PropertySpec.Builder? = null

    protected open fun signalValBuilder(intf: Interface, signal: Signal): PropertySpec.Builder? =
        null

    protected abstract fun FunSpec.Builder.buildRegistration(intf: Interface)

    protected fun intfName(intf: Interface) =
        ClassName(intf.name.pkg, intf.name.simpleName).nestedClass("Companion")
            .nestedClass("INTERFACE_NAME")

    protected fun Signal.signalName() = name.replaceFirstChar {
        if (it.isLowerCase()) {
            it.titlecase(Locale.getDefault())
        } else {
            it.toString()
        }
    }
}

val String.pkg: String
    get() {
        return split(".").dropLast(1).joinToString(".").lowercase()
    }
