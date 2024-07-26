package com.monkopedia.sdbus

import com.monkopedia.sdbus.NamingManager.GeneratedType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

fun GeneratedType.generateType(): FileSpec {
    return FileSpec.builder(reference.packageName, reference.simpleName).apply {
        addType(
            TypeSpec.classBuilder(reference.simpleName).apply {
                addModifiers(KModifier.DATA)
                addAnnotation(ClassName("kotlinx.serialization", "Serializable"))
                val constructorBuilder = FunSpec.constructorBuilder()

                for ((name, type) in args) {
                    constructorBuilder.addParameter(name, type)
                    addProperty(PropertySpec.builder(name, type).initializer("%N", name).build())
                }

                primaryConstructor(constructorBuilder.build())
            }.build()
        )
    }.build()
}
