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
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.KModifier.SUSPEND
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Builder
import com.squareup.kotlinpoet.joinToCode
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

    /**
     * Writable properties whose `org.freedesktop.DBus.Property.EmitsChangedSignal` annotation is not
     * `false`/`const` get a concrete backing property delegated to [com.monkopedia.sdbus.notifying],
     * so that BOTH a remote `Properties.Set` (routed through the vtable setter bound in [register])
     * AND a server-side assignment auto-emit `PropertiesChanged`. Read-only properties, properties
     * that opt out of the signal, and properties whose type has no representable default value are
     * left abstract for the implementor to provide.
     */
    override fun propertyBuilder(intf: Interface, prop: Property): PropertySpec.Builder? {
        if (prop.access != READWRITE && prop.access != WRITE) return null
        if (!prop.emitsChangedSignal(intf)) return null
        val default = prop.defaultValue() ?: return null
        return PropertySpec.builder(prop.name.decapitalCamelCase, namingManager[prop]).apply {
            addModifiers(OVERRIDE)
            mutable(true)
            delegate(
                CodeBlock.builder()
                    .add(
                        "%N.%M(%T, %T(%S), ",
                        "obj",
                        MemberName("com.monkopedia.sdbus", "notifying"),
                        intfName(intf),
                        ClassName("com.monkopedia.sdbus", "PropertyName"),
                        prop.name
                    )
                    .add(default)
                    .add(")")
                    .build()
            )
        }
    }

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
                            "asyncCall(this@%N::%N)\n",
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

    private companion object {
        private const val EMITS_CHANGED_SIGNAL =
            "org.freedesktop.DBus.Property.EmitsChangedSignal"

        /**
         * Resolves the effective `EmitsChangedSignal` value for [this] property: a property-level
         * annotation wins, otherwise the enclosing interface's annotation provides the default,
         * otherwise the D-Bus default is `true`. Returns `true` unless the value is `false`/`const`
         * (the two values for which the spec says no `PropertiesChanged` is emitted).
         */
        private fun Property.emitsChangedSignal(intf: Interface): Boolean {
            val value = annotations.firstOrNull { it.name == EMITS_CHANGED_SIGNAL }?.value
                ?: intf.annotations.firstOrNull { it.name == EMITS_CHANGED_SIGNAL }?.value
                ?: "true"
            return value != "false" && value != "const"
        }

        /**
         * A representable initial value for the [notifying] delegate backing a writable property, or
         * `null` for types with no obvious default (so the property is left abstract instead of
         * generating uncompilable code).
         */
        private fun Property.defaultValue(): CodeBlock? = when (type) {
            "b" -> CodeBlock.of("false")
            "y" -> CodeBlock.of("0.toUByte()")
            "n" -> CodeBlock.of("0.toShort()")
            "q" -> CodeBlock.of("0.toUShort()")
            "i" -> CodeBlock.of("0")
            "u" -> CodeBlock.of("0u")
            "x" -> CodeBlock.of("0L")
            "t" -> CodeBlock.of("0uL")
            "d" -> CodeBlock.of("0.0")
            "s" -> CodeBlock.of("%S", "")
            "o" -> CodeBlock.of("%T(%S)", ClassName("com.monkopedia.sdbus", "ObjectPath"), "/")
            "g" -> CodeBlock.of("%T(%S)", ClassName("com.monkopedia.sdbus", "Signature"), "")
            else -> null
        }
    }
}
