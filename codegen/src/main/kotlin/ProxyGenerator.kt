package com.monkopedia.sdbus

import com.monkopedia.sdbus.Access.READWRITE
import com.monkopedia.sdbus.Access.WRITE
import com.monkopedia.sdbus.Direction.OUT
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.KModifier.*

class ProxyGenerator : BaseGenerator() {

    private val iProxy = ClassName.bestGuess("com.monkopedia.sdbus.IProxy")
    override val fileSuffix: String
        get() = "Proxy"

    override fun classBuilder(intf: Interface): TypeSpec.Builder =
        TypeSpec.classBuilder(intf.name.simpleName + "Proxy").apply {
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
                PropertySpec.builder("proxy", iProxy).apply {
                    initializer(CodeBlock.of("proxy"))
                    addModifiers(PROTECTED)
                }.build()
            )
        }

    override fun constructorBuilder(intf: Interface): FunSpec.Builder =
        FunSpec.constructorBuilder().apply {
            addParameter(
                "proxy",
                iProxy
            )
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
                        "return proxy.%T(%T, %S) { ",
                        ClassName("com.monkopedia.sdbus", "callMethodAsync"),
                        intfName(intf),
                        method.name
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

    override fun propertyBuilder(intf: Interface, method: Property): PropertySpec.Builder =
        PropertySpec.builder(method.name.decapitalCamelCase, namingManager[method]).apply {
            addModifiers(OVERRIDE)
            if (method.access == WRITE || method.access == READWRITE) {
                mutable(true)
            }
            delegate(
                "proxy.%T(%T, %S) ",
                ClassName("com.monkopedia.sdbus", "prop"),
                intfName(intf),
                method.name
            )
        }

    override fun signalBuilder(intf: Interface, signal: Signal): FunSpec.Builder? = null

    override fun FunSpec.Builder.buildRegistration(intf: Interface) {
        addModifiers(PUBLIC)
        addModifiers(OVERRIDE)
        addCode("val weakRef = %T(this)\n", ClassName.bestGuess("kotlin.native.ref.WeakReference"))
        intf.signals.forEach { signal ->
            val args = signal.args.filter { it.direction != OUT }
            addCode(
                CodeBlock.builder().apply {
                    add(
                        "proxy.%T(%T, %S) {\n",
                        ClassName("com.monkopedia.sdbus", "onSignal"),
                        intfName(intf),
                        signal.name
                    )
                    withIndent {
                        add("acall {\n")
                        withIndent {
                            withIndent {
                                args.forEach {
                                    add("${it.name!!.decapitalCamelCase}: %T,\n", namingManager[it])
                                }
                            }
                            add("->\n")
                            add("weakRef.get()\n")
                            withIndent {
                                add(
                                    "?.on${signal.signalName()}(${
                                        args.joinToString(", ") {
                                            it.name!!.decapitalCamelCase
                                        }
                                    })\n"
                                )
                                add("?: Unit\n")
                            }
                        }
                        add("}\n")
                    }
                    add("}\n")
                }.build()
            )
        }
    }
}
