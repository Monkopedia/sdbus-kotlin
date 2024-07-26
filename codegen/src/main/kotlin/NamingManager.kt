package com.monkopedia.sdbus

import com.monkopedia.sdbus.Direction.IN
import com.monkopedia.sdbus.Direction.OUT
import com.monkopedia.sdbus.NamingManager.GeneratedType
import com.monkopedia.sdbus.NamingManager.LazyType
import com.monkopedia.sdbus.NamingManager.NamingType
import com.monkopedia.sdbus.NamingManager.SimpleType
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.Dynamic
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.U_BYTE
import com.squareup.kotlinpoet.U_INT
import com.squareup.kotlinpoet.U_LONG
import com.squareup.kotlinpoet.U_SHORT
import com.squareup.kotlinpoet.WildcardTypeName

class NamingManager(doc: XmlRootNode) {

    sealed class NamingType {
        abstract val type: String
        abstract val reference: TypeName
    }

    data class SimpleType(override val type: String, override val reference: TypeName) :
        NamingType()

    class GeneratedType(
        val pkgs: MutableList<String>,
        val nameReferences: MutableSet<String>,
        private val baseTypes: List<NamingType>,
        override val type: String
    ) : NamingType() {
        private val nameSuggestions = mutableListOf<List<Arg>>()
        lateinit var args: List<Pair<String, TypeName>>
            private set

        fun suggestNames(list: List<Arg>) {
            nameSuggestions.add(list)
        }

        fun generateName(usedNames: MutableList<String>) {
            val argNames =
                nameSuggestions.firstOrNull { it.size == baseTypes.size }?.map { it.name } ?: List(
                    baseTypes.size
                ) { null }
            args = argNames.zip(baseTypes.map { it.reference }).map { (name, reference) ->
                val actualName = name ?: reference.asVarName
                actualName to reference
            }
            val pkg = pkgs.groupBy { it }.values.maxByOrNull { it.single() }?.firstOrNull()
                ?: "sdbus.generated"
            if (nameReferences.size == 1) {
                val selectedName = nameReferences.single().capitalCamelCase
                reference = ClassName(pkg, selectedName)
            } else if (nameReferences.size == 0) {
                error("No name references")
            } else {
                val mostCommon = nameReferences.reduce { acc, s ->
                    acc.commonPrefixWith(s)
                }
                if (mostCommon.length > 2) {
                    reference = ClassName(pkg, mostCommon.capitalCamelCase + "Type")
                } else {
                    val mostCommonCaseless = nameReferences.reduce { acc, s ->
                        acc.commonPrefixWith(s, ignoreCase = true)
                    }
                    if (mostCommonCaseless.length > 2) {
                        reference = ClassName(pkg, mostCommonCaseless.capitalCamelCase + "Type")
                    } else {
                        val shortest =
                            nameReferences.filter { it.length > 2 }.minByOrNull { it.length }
                                ?: nameReferences.first()
                        reference = ClassName(pkg, shortest.capitalCamelCase + "Type")
                    }
                }
            }
            var index = 0
            while (reference.toString() in usedNames) {
                reference = ClassName(reference.packageName, reference.simpleName + "${++index}")
            }
        }

        private val TypeName.asVarName: String
            get() = when (this) {
                is ClassName -> simpleName.decapitalized
                is ParameterizedTypeName -> toString().decapitalized
                is TypeVariableName -> toString().decapitalized
                is LambdaTypeName -> error("No lambda")
                Dynamic -> error("No dynamic")
                is WildcardTypeName -> error("No wildcard")
            }

        override lateinit var reference: ClassName
            private set
    }

    data class LazyType(
        override val type: String,
        val baseTypes: List<NamingType>,
        val generator: (List<TypeName>) -> TypeName
    ) : NamingType() {
        override val reference: TypeName by lazy {
            generator(baseTypes.map { it.reference })
        }
    }

    val typeMap: Map<String, NamingType> = buildMap {
        buildRootTypes(doc)
    }

    val extraFiles: List<FileSpec>
        get() = typeMap.values.filterIsInstance<GeneratedType>().map { it.generateType() }

    init {
        val usedNames = mutableListOf<String>()
        typeMap.values.filterIsInstance<GeneratedType>()
            .sortedByDescending { it.nameReferences.size }
            .forEach { it.generateName(usedNames) }
    }

    operator fun get(arg: Arg): TypeName =
        typeMap[arg.type]?.reference ?: error("Unexpected argument ${arg.type}")

    operator fun get(outputs: List<Arg>): TypeName {
        if (outputs.isEmpty()) return UNIT
        if (outputs.size == 1) return this[outputs.first()]
        val key = structKey(outputs.map { typeMap[it.type] ?: error("Unexpected argument $it") })
        return typeMap[key]?.reference ?: error("Unexpected argument $key")
    }

    operator fun get(method: Property): TypeName =
        typeMap[method.type]?.reference ?: error("Unexpected argument ${method.type}")
}

private fun MutableMap<String, NamingType>.buildRootTypes(node: XmlRootNode) {
    buildRootsTypes(node.nodes)
    buildInterfacesTypes(node.interfaces)
}

private fun MutableMap<String, NamingType>.buildRootsTypes(nodes: List<XmlRootNode>) {
    for (node in nodes) {
        buildRootTypes(node)
    }
}

private fun MutableMap<String, NamingType>.buildInterfacesTypes(intfs: List<Interface>) {
    for (intf in intfs) {
        buildInterfaceTypes(intf)
    }
}

private fun MutableMap<String, NamingType>.buildInterfaceTypes(intf: Interface) {
    buildSignalsTypes(intf.name.pkg, intf.signals)
    buildMethodsTypes(intf.name.pkg, intf.methods)
    buildPropertiesTypes(intf.name.pkg, intf.properties)
    buildAnnotationsTypes(intf.name.pkg, intf.annotations)
}

private fun MutableMap<String, NamingType>.buildAnnotationsTypes(
    pkg: String,
    annotations: List<Annotation>
) {
    annotations.forEach {
        buildAnnotationTypes(pkg, it)
    }
}

private fun MutableMap<String, NamingType>.buildPropertiesTypes(
    pkg: String,
    properties: List<Property>
) {
    properties.forEach {
        buildPropertyTypes(pkg, it)
    }
}

private fun MutableMap<String, NamingType>.buildMethodsTypes(pkg: String, methods: List<Method>) {
    methods.forEach {
        buildMethodTypes(pkg, it)
    }
}

private fun MutableMap<String, NamingType>.buildSignalsTypes(pkg: String, signals: List<Signal>) {
    signals.forEach {
        buildSignalTypes(pkg, it)
    }
}

private fun MutableMap<String, NamingType>.buildAnnotationTypes(
    pkg: String,
    annotation: Annotation
) {
}

private fun MutableMap<String, NamingType>.buildPropertyTypes(pkg: String, property: Property) {
    buildType(pkg, property.name, property.type)
    buildAnnotationsTypes(pkg, property.annotations)
}

private fun MutableMap<String, NamingType>.buildMethodTypes(pkg: String, method: Method) {
    method.args.filter { it.direction == IN }.forEachIndexed { index, arg ->
        buildArgTypes(pkg, index, arg)
    }
    buildSingleType(pkg, method.name, method.args.filter { it.direction == OUT })
    buildAnnotationsTypes(pkg, method.annotations)
}

private fun MutableMap<String, NamingType>.buildSignalTypes(pkg: String, signal: Signal) {
    buildSingleType(pkg, signal.name, signal.args)
    buildAnnotationsTypes(pkg, signal.annotations)
}

private fun MutableMap<String, NamingType>.buildArgTypes(pkg: String, index: Int, arg: Arg) {
    buildType(pkg, arg.name ?: "arg$index", arg.type)
    buildAnnotationsTypes(pkg, arg.annotations)
}

private fun MutableMap<String, NamingType>.buildSingleType(
    pkg: String,
    name: String,
    outputs: List<Arg>
) {
    if (outputs.isEmpty()) return
    if (outputs.size == 1) {
        return buildArgTypes(pkg, 0, outputs.first())
    }
    val type = generatedType(pkg, outputs.map { buildType(pkg, it.name ?: "", it.type) }, name)
        .also {
            it.suggestNames(outputs)
        }
    this[type.type] = type
}

fun MutableMap<String, NamingType>.buildType(pkg: String, name: String, type: String): NamingType {
    (this[type] as? SimpleType)?.let { return it }
    if (type == "") {
        this[type] = SimpleType(type, UNIT)
    }
    val namingType: NamingType = when (val typeStart = type[0]) {
        'b' -> SimpleType(typeStart.toString(), BOOLEAN)
        'y' -> SimpleType(typeStart.toString(), U_BYTE)
        'n' -> SimpleType(typeStart.toString(), SHORT)
        'q' -> SimpleType(typeStart.toString(), U_SHORT)
        'i' -> SimpleType(typeStart.toString(), INT)
        'u' -> SimpleType(typeStart.toString(), U_INT)
        'x' -> SimpleType(typeStart.toString(), LONG)
        't' -> SimpleType(typeStart.toString(), U_LONG)
        'd' -> SimpleType(typeStart.toString(), DOUBLE)
        's' -> SimpleType(typeStart.toString(), STRING)
        'o' -> SimpleType(
            typeStart.toString(),
            ClassName.bestGuess("com.monkopedia.sdbus.ObjectPath")
        )

        'g' -> SimpleType(
            typeStart.toString(),
            ClassName.bestGuess("com.monkopedia.sdbus.Signature")
        )

        'h' -> SimpleType(typeStart.toString(), ClassName.bestGuess("com.monkopedia.sdbus.UnixFd"))
        'v' -> SimpleType(typeStart.toString(), ClassName.bestGuess("com.monkopedia.sdbus.Variant"))
        'a' -> {
            if (type[1] == '{') {
                // Map
                val keyTypeStr = type.substring(2)
                val keyType = buildType(pkg, name + "Key", keyTypeStr)
                require(keyTypeStr.startsWith(keyType.type)) {
                    "Invalid type parsed out, expected $keyTypeStr but got ${keyType.type}"
                }
                val valueTypeStr = keyTypeStr.substring(keyType.type.length)
                val valueType = buildType(pkg, name + "Value", valueTypeStr)
                require(valueTypeStr.startsWith(valueType.type)) {
                    "Invalid type parsed out, expected $valueTypeStr but got ${valueType.type}"
                }
                require(valueTypeStr[valueType.type.length] == '}') {
                    "Map did not close after value."
                }
                LazyType(
                    "a{${keyType.type}${valueType.type}}",
                    listOf(keyType, valueType)
                ) { types ->
                    MAP.parameterizedBy(*types.toTypedArray())
                }
            } else {
                // List
                val valueTypeStr = type.substring(1)
                val valueType = buildType(pkg, name + "Value", valueTypeStr)
                require(valueTypeStr.startsWith(valueType.type)) {
                    "Invalid type parsed out, expected $valueTypeStr but got ${valueType.type}"
                }
                LazyType("a${valueType.type}", listOf(valueType)) { types ->
                    LIST.parameterizedBy(types.single())
                }
            }
        }

        '(' -> {
            var index = 0
            val types = sequence {
                var currentType = type.substring(1)
                while (currentType.isNotEmpty() && currentType[0] != ')') {
                    val nextType = buildType(pkg, name + "${index++}", currentType)
                    require(currentType.startsWith(nextType.type)) {
                        "Invalid type parsed out, expected $currentType but got ${nextType.type}"
                    }
                    yield(nextType)
                    currentType = currentType.substring(nextType.type.length)
                }
                require(currentType.isNotEmpty()) {
                    "Struct not closed properly: $type"
                }
            }.toList()
            generatedType(pkg, types, name)
        }

        else -> error("Unsupported type $type")
    }
    this[namingType.type] = namingType
    return namingType
}

private fun MutableMap<String, NamingType>.generatedType(
    pkg: String,
    types: List<NamingType>,
    name: String
): GeneratedType {
    val key = structKey(types)
    val existingType = this[key]
    return if (existingType is GeneratedType) {
        existingType.apply {
            nameReferences.add(name)
            pkgs.add(pkg)
        }
    } else {
        GeneratedType(mutableListOf(pkg), mutableSetOf(name), types, key)
    }
}

private fun structKey(types: List<NamingType>) = "(${types.joinToString("") { it.type }})"
