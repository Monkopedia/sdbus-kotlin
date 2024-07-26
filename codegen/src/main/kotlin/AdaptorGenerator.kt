package com.monkopedia.sdbus

import com.monkopedia.sdbus.Direction.OUT
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PROTECTED
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.KModifier.SUSPEND
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeSpec.Builder
import com.squareup.kotlinpoet.withIndent

class AdaptorGenerator : BaseGenerator() {

    private val iObject = ClassName.bestGuess("com.monkopedia.sdbus.IObject")
    override val fileSuffix: String
        get() = "Adaptor"

    override fun classBuilder(intf: Interface): Builder =
        TypeSpec.classBuilder(intf.name.simpleName + "Adaptor").apply {
            addAnnotation(
                AnnotationSpec.builder(ClassName.bestGuess("kotlin.OptIn")).apply {
                    addMember(
                        "%T::class",
                        ClassName.bestGuess("kotlin.experimental.ExperimentalNativeApi")
                    )
                }.build()
            )
            addSuperinterface(ClassName(intf.name.pkg, intf.name.simpleName))
            addModifiers(ABSTRACT)
            addProperty(
                PropertySpec.builder("obj", iObject).apply {
                    initializer(CodeBlock.of("obj"))
                    addModifiers(PROTECTED)
                }.build()
            )
        }

    override fun constructorBuilder(intf: Interface): FunSpec.Builder =
        FunSpec.constructorBuilder().apply {
            addParameter(
                "obj",
                iObject
            )
        }

    override fun methodBuilder(intf: Interface, method: Method): FunSpec.Builder? = null

    override fun propertyBuilder(intf: Interface, method: Property): PropertySpec.Builder? = null

    override fun signalBuilder(intf: Interface, signal: Signal): FunSpec.Builder = FunSpec.builder(
        "on" + signal.signalName()
    ).apply {
        addModifiers(SUSPEND)
        addModifiers(OVERRIDE)
        val params = signal.args.filter { it.direction != OUT }.withIndex()
        for ((index, arg) in params) {
            addParameter((arg.name ?: "arg$index").decapitalCamelCase, namingManager[arg])
        }
        addCode(
            CodeBlock.builder().apply {
                add(
                    "return obj.%T(%S) { ",
                    ClassName("com.monkopedia.sdbus", "emitSignal"),
                    signal.name
                )
                withIndent {
                    add(
                        "call(${
                            params.joinToString(", ") {
                                (it.value.name ?: "arg${it.index}").decapitalCamelCase
                            }
                        }) "
                    )
                }
                add("}")
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
                    add("%T(%S) {\n", ClassName("com.monkopedia.sdbus", "method"), it.name)
                    withIndent {
                        val args = it.args.filter { it.direction != OUT }
                        if (args.all { !it.name.isNullOrBlank() }) {
                            val argsTempl = List(args.size) { "%S" }.joinToString(", ")
                            add(
                                "inputParamNames = listOf($argsTempl)\n",
                                *args.map { it.name }.toTypedArray()
                            )
                        }
                        val outs = it.args.filter { it.direction == OUT }
                        if (outs.all { !it.name.isNullOrBlank() }) {
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
                    add("%T(%S) {\n", ClassName("com.monkopedia.sdbus", "signal"), it.name)
                    withIndent {
                        it.args.forEachIndexed { index, arg ->
                            add("with<%T>(%S)\n", namingManager[arg], arg.name ?: "arg$index")
                        }
                    }
                    add("}\n")
                }
                intf.properties.forEach {
                    add("%T(%S) {\n", ClassName("com.monkopedia.sdbus", "prop"), it.name)
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
