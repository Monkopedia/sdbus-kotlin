package com.monkopedia.sdbus

import com.monkopedia.sdbus.Access.READWRITE
import com.monkopedia.sdbus.Access.WRITE
import com.monkopedia.sdbus.Direction.OUT
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.KModifier.*
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
                        PropertySpec.builder("INTERFACE_NAME", STRING).apply {
                            addModifiers(CONST)
                            initializer("%S", intf.name)
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

    override fun signalBuilder(intf: Interface, signal: Signal): FunSpec.Builder = FunSpec.builder(
        "on" + signal.signalName()
    ).apply {
        addModifiers(SUSPEND)
        addModifiers(ABSTRACT)
        for ((index, arg) in signal.args.filter { it.direction != OUT }.withIndex()) {
            addParameter((arg.name ?: "arg$index").decapitalCamelCase, namingManager[arg])
        }
    }

    override fun FunSpec.Builder.buildRegistration(intf: Interface) {
        addModifiers(ABSTRACT)
    }
}
