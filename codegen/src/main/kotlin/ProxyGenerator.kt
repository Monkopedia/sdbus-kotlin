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
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.KModifier.SUSPEND
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.withIndent

class ProxyGenerator : BaseGenerator() {

    private val proxy = ClassName.bestGuess("com.monkopedia.sdbus.Proxy")
    override val fileSuffix: String
        get() = "Proxy"

    override fun classBuilder(intf: Interface): TypeSpec.Builder =
        TypeSpec.classBuilder(intf.name.simpleName + "Proxy").apply {
            addSuperinterface(ClassName(intf.name.pkg, intf.name.simpleName))
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
                addParameter((arg.name ?: "arg$index").decapitalCamelCase, namingManager[arg])
            }
            val outputs = method.args.filter { it.direction == OUT }
            returns(namingManager[outputs])
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

    override fun propertyBuilder(intf: Interface, method: Property): PropertySpec.Builder =
        PropertySpec.builder(method.name.decapitalCamelCase, namingManager[method]).apply {
            addModifiers(OVERRIDE)
            if (method.access == WRITE || method.access == READWRITE) {
                mutable(true)
            }
            delegate(
                "proxy.%T(%T, %T(%S)) ",
                ClassName("com.monkopedia.sdbus", "prop"),
                intfName(intf),
                ClassName("com.monkopedia.sdbus", "PropertyName"),
                method.name
            )
        }

    override fun signalBuilder(intf: Interface, signal: Signal): FunSpec.Builder? = null

    override fun signalValBuilder(intf: Interface, signal: Signal): PropertySpec.Builder? {
        val type = namingManager.get(signal.args)
        return PropertySpec.builder(
            signal.name.decapitalCamelCase,
            ClassName.bestGuess("kotlinx.coroutines.flow.Flow").parameterizedBy(type)
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
                        if (type == UNIT) {
                            add("call { -> Unit }\n")
                        } else {
                            add("call(::%T)\n", type)
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
