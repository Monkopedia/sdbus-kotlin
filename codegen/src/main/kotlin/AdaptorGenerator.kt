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

import com.monkopedia.sdbus.Direction.OUT
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.KModifier.SUSPEND
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Builder
import com.squareup.kotlinpoet.withIndent

class AdaptorGenerator(packageOverride: String? = null) : BaseGenerator(packageOverride) {

    private val obj = ClassName.bestGuess("com.monkopedia.sdbus.Object")
    override val fileSuffix: String
        get() = "Adaptor"

    override fun classBuilder(intf: Interface): Builder =
        TypeSpec.classBuilder(intf.name.simpleName + "Adaptor").apply {
            addSuperinterface(ClassName(kotlinPackage(intf), intf.name.simpleName))
            addModifiers(ABSTRACT)
            addProperty(
                PropertySpec.builder("obj", obj).apply {
                    initializer(CodeBlock.of("obj"))
                    addModifiers(PUBLIC)
                }.build()
            )
        }

    override fun constructorBuilder(intf: Interface): FunSpec.Builder =
        FunSpec.constructorBuilder().apply {
            addParameter("obj", obj)
        }

    override fun methodBuilder(intf: Interface, method: Method): FunSpec.Builder? = null

    override fun propertyBuilder(intf: Interface, prop: Property): PropertySpec.Builder? = null

    override fun signalBuilder(intf: Interface, signal: Signal): FunSpec.Builder = FunSpec.builder(
        "on" + signal.signalName()
    ).apply {
        addModifiers(SUSPEND)
        addModifiers(PUBLIC)
        val params = signal.args.filter { it.direction != OUT }.withIndex()
        for ((index, arg) in params) {
            addParameter((arg.name ?: "arg$index").decapitalCamelCase, namingManager[arg].reference)
        }
        addCode(
            CodeBlock.builder().apply {
                add(
                    "return obj.%T(%T, %T(%S)) {\n",
                    ClassName("com.monkopedia.sdbus", "emitSignal"),
                    intfName(intf),
                    ClassName("com.monkopedia.sdbus", "SignalName"),
                    signal.name
                )
                withIndent {
                    add(
                        "call(${
                            params.joinToString(", ") {
                                (it.value.name ?: "arg${it.index}").decapitalCamelCase
                            }
                        })\n"
                    )
                }
                add("}\n")
            }.build()
        )
    }

    override fun FunSpec.Builder.buildRegistration(intf: Interface) {
        addModifiers(PUBLIC)
        addModifiers(OVERRIDE)
        val codeBlock = CodeBlock.builder().apply {
            add("obj.%T(%T) {\n", ClassName("com.monkopedia.sdbus", "addVTable"), intfName(intf))
            withIndent {
                intf.methods.forEach {
                    add(
                        "%T(%T(%S)) {\n",
                        ClassName("com.monkopedia.sdbus", "method"),
                        ClassName("com.monkopedia.sdbus", "MethodName"),
                        it.name
                    )
                    withIndent {
                        val args = it.args.filter { it.direction != OUT }
                        if (args.all { !it.name.isNullOrBlank() } && args.isNotEmpty()) {
                            val argsTempl = List(args.size) { "%S" }.joinToString(", ")
                            add(
                                "inputParamNames = listOf($argsTempl)\n",
                                *args.map { it.name }.toTypedArray()
                            )
                        }
                        val outs = it.args.filter { it.direction == OUT }
                        if (outs.all { !it.name.isNullOrBlank() } && outs.isNotEmpty()) {
                            val argsTempl = List(outs.size) { "%S" }.joinToString(", ")
                            add(
                                "outputParamNames = listOf($argsTempl)\n",
                                *outs.map { it.name }.toTypedArray()
                            )
                        }
                        add(
                            "acall(this@%N::%N)\n",
                            intf.name.simpleName + "Adaptor",
                            it.name.decapitalCamelCase
                        )
                    }
                    add("}\n")
                }
                intf.signals.forEach {
                    add(
                        "%T(%T(%S)) {\n",
                        ClassName("com.monkopedia.sdbus", "signal"),
                        ClassName("com.monkopedia.sdbus", "SignalName"),
                        it.name
                    )
                    withIndent {
                        it.args.forEachIndexed { index, arg ->
                            add(
                                "with<%T>(%S)\n",
                                namingManager[arg].reference,
                                arg.name ?: "arg$index"
                            )
                        }
                    }
                    add("}\n")
                }
                intf.properties.forEach {
                    add(
                        "%T(%T(%S)) {\n",
                        ClassName("com.monkopedia.sdbus", "prop"),
                        ClassName("com.monkopedia.sdbus", "PropertyName"),
                        it.name
                    )
                    withIndent {
                        add(
                            "with(this@%N::%N)\n",
                            intf.name.simpleName + "Adaptor",
                            it.name.decapitalCamelCase
                        )
                    }
                    add("}\n")
                }
            }
            add("}\n")
        }.build()
        addCode(codeBlock)
    }
}
