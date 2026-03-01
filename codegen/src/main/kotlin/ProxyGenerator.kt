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

import com.monkopedia.sdbus.Access.READWRITE
import com.monkopedia.sdbus.Access.WRITE
import com.monkopedia.sdbus.Direction.OUT
import com.monkopedia.sdbus.NamingManager.SimpleType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.KModifier.SUSPEND
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.withIndent

class ProxyGenerator(packageOverride: String? = null) : BaseGenerator(packageOverride) {

    private val proxy = ClassName.bestGuess("com.monkopedia.sdbus.Proxy")
    override val fileSuffix: String
        get() = "Proxy"

    private fun Interface.baseName(): String = name.simpleName + "Proxy"

    override fun classBuilder(intf: Interface): TypeSpec.Builder =
        TypeSpec.classBuilder(intf.baseName()).apply {
            addSuperinterface(ClassName(kotlinPackage(intf), intf.name.simpleName))
            addProperty(
                PropertySpec.builder("proxy", proxy).apply {
                    initializer(CodeBlock.of("proxy"))
                    addModifiers(PUBLIC)
                }.build()
            )
        }

    override fun constructorBuilder(intf: Interface): FunSpec.Builder =
        FunSpec.constructorBuilder().apply {
            addParameter("proxy", proxy)
        }

    override fun methodBuilder(intf: Interface, method: Method): FunSpec.Builder =
        FunSpec.builder(method.name.decapitalCamelCase).apply {
            addModifiers(OVERRIDE)
            addModifiers(SUSPEND)
            val params = method.args.filter { it.direction != OUT }.withIndex()
            for ((index, arg) in params) {
                addParameter(
                    (arg.name ?: "arg$index").decapitalCamelCase,
                    namingManager[arg].reference
                )
            }
            val outputs = method.args.filter { it.direction == OUT }
            returns(namingManager[outputs].reference)
            addCode(
                CodeBlock.builder().apply {
                    add(
                        "return proxy.%T(%T, %T(%S)) {\n",
                        ClassName("com.monkopedia.sdbus", "callMethodAsync"),
                        intfName(intf),
                        ClassName("com.monkopedia.sdbus", "MethodName"),
                        method.name
                    )
                    withIndent {
                        if (outputs.size > 1) {
                            add("isGroupedReturn = true\n")
                        }

                        val paramCode = params.map {
                            val name = it.value.name ?: "arg${it.index}"
                            name.decapitalCamelCase
                        }.map { CodeBlock.of("%N", it) }

                        addStatement(
                            "call(%L)",
                            paramCode.joinToCode()
                        )
                    }
                    add("}\n")
                }.build()
            )
        }

    override fun extraPropertyBuilder(intf: Interface, prop: Property): PropertySpec.Builder? {
        val mutable = prop.access == WRITE || prop.access == READWRITE
        val type = namingManager[prop]
        val propertyDelegateType = ClassName(
            "com.monkopedia.sdbus",
            if (mutable) "MutablePropertyDelegate" else "PropertyDelegate"
        )
        return PropertySpec.builder(
            prop.name.decapitalCamelCase + "Property",
            propertyDelegateType.parameterizedBy(ClassName.bestGuess(intf.baseName()), type)
        ).apply {
            mutable(mutable)
            initializer(
                "proxy.%T(%T, %T(%S)) ",
                ClassName(
                    "com.monkopedia.sdbus",
                    if (mutable) "mutableDelegate" else "propDelegate"
                ),
                intfName(intf),
                ClassName("com.monkopedia.sdbus", "PropertyName"),
                prop.name
            )
        }
    }

    override fun propertyBuilder(intf: Interface, prop: Property): PropertySpec.Builder =
        PropertySpec.builder(prop.name.decapitalCamelCase, namingManager[prop]).apply {
            addModifiers(OVERRIDE)
            if (prop.access == WRITE || prop.access == READWRITE) {
                mutable(true)
            }
            delegate(prop.name.decapitalCamelCase + "Property")
        }

    override fun signalBuilder(intf: Interface, signal: Signal): FunSpec.Builder? = null

    override fun signalValBuilder(intf: Interface, signal: Signal): PropertySpec.Builder? {
        val type = namingManager[signal.args]
        return PropertySpec.builder(
            signal.name.decapitalCamelCase,
            ClassName.bestGuess("kotlinx.coroutines.flow.Flow").parameterizedBy(type.reference)
        ).apply {
            addModifiers(PUBLIC)
            initializer(
                CodeBlock.builder().apply {
                    add(
                        "proxy.%T(%T, %T(%S)) {\n",
                        ClassName("com.monkopedia.sdbus", "signalFlow"),
                        intfName(intf),
                        ClassName("com.monkopedia.sdbus", "SignalName"),
                        signal.name
                    )
                    withIndent {
                        if (type.reference == UNIT) {
                            add("call { -> Unit }\n")
                        } else if (type is SimpleType) {
                            add("call { a: %T -> a }\n", type.reference)
                        } else {
                            add("call(::%T)\n", type.reference)
                        }
                    }
                    add("}\n")
                }.build()
            )
        }
    }

    override fun FunSpec.Builder.buildRegistration(intf: Interface) {
        addModifiers(PUBLIC)
        addModifiers(OVERRIDE)
    }
}
