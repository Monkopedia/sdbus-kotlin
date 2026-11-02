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

import com.monkopedia.sdbus.Direction.IN
import com.monkopedia.sdbus.Direction.OUT
import com.monkopedia.sdbus.NamingManager.AliasType
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

/**
 * Resolves every D-Bus signature in [doc] to the Kotlin type that represents it, generating a name
 * for the ones that need a class of their own.
 *
 * When [honorNamingAnnotations] is set the naming annotations the XML carries are applied on top of
 * the derived names; see [applyNamingHints]. It is opt-in because a hint can only ever *replace* a
 * name the generator would otherwise derive, which would be a source break for anyone already
 * compiling against generated output. With it unset nothing on that path runs.
 */
class NamingManager(
    doc: XmlRootNode,
    packageOverride: String? = null,
    honorNamingAnnotations: Boolean = false
) {

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

        /**
         * Name taken from a naming annotation, replacing whatever [nameReferences] would derive.
         * Only ever set when the generator was asked to honor naming annotations.
         */
        var nameHint: String? = null

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
                ?.lowercase()
                ?: "sdbus.generated"
            val hint = nameHint
            if (hint != null) {
                reference = ClassName(pkg, hint.capitalCamelCase)
            } else if (nameReferences.size == 1) {
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
                is ParameterizedTypeName -> rawType.asVarName

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

    /**
     * A signature a naming annotation named but that generates no class of its own — a map, a list
     * or a primitive. The name is emitted as a `typealias` for [aliased] so that it can be used
     * without changing the type it stands for.
     */
    data class AliasType(
        override val type: String,
        override val reference: ClassName,
        val aliased: NamingType
    ) : NamingType()

    val typeMap: Map<String, NamingType> = buildMap {
        buildRootTypes(doc, packageOverride)
        if (honorNamingAnnotations) {
            applyNamingHints(doc, packageOverride)
        }
    }

    val extraFiles: List<FileSpec>
        get() = typeMap.values.filterIsInstance<GeneratedType>().map { it.generateType() } +
            typeMap.values.filterIsInstance<AliasType>().map { it.generateAlias() }

    init {
        // Empty unless a naming annotation introduced an alias, so a derived name only ever has to
        // dodge a collision on the opt-in path.
        val usedNames = typeMap.values.filterIsInstance<AliasType>()
            .mapTo(mutableListOf()) { it.reference.toString() }
        typeMap.values.filterIsInstance<GeneratedType>()
            .sortedByDescending { it.nameReferences.size }
            .forEach { it.generateName(usedNames) }
    }

    operator fun get(arg: Arg): NamingType =
        typeMap[arg.type] ?: error("Unexpected argument ${arg.type}")

    operator fun get(outputs: List<Arg>): NamingType {
        if (outputs.isEmpty()) return SimpleType("", UNIT)
        if (outputs.size == 1) return this[outputs.first()]
        val key = structKey(outputs.map { typeMap[it.type] ?: error("Unexpected argument $it") })
        return typeMap[key] ?: error("Unexpected argument $key")
    }

    operator fun get(method: Property): TypeName =
        typeMap[method.type]?.reference ?: error("Unexpected argument ${method.type}")
}

private fun MutableMap<String, NamingType>.buildRootTypes(
    node: XmlRootNode,
    packageOverride: String?
) {
    buildRootsTypes(node.nodes, packageOverride)
    buildInterfacesTypes(node.interfaces, packageOverride)
}

private fun MutableMap<String, NamingType>.buildRootsTypes(
    nodes: List<XmlRootNode>,
    packageOverride: String?
) {
    for (node in nodes) {
        buildRootTypes(node, packageOverride)
    }
}

private fun MutableMap<String, NamingType>.buildInterfacesTypes(
    intfs: List<Interface>,
    packageOverride: String?
) {
    for (intf in intfs) {
        buildInterfaceTypes(intf, packageOverride)
    }
}

private fun MutableMap<String, NamingType>.buildInterfaceTypes(
    intf: Interface,
    packageOverride: String?
) {
    val pkg = packageOverride ?: intf.name.pkg
    buildSignalsTypes(pkg, intf.signals)
    buildMethodsTypes(pkg, intf.methods)
    buildPropertiesTypes(pkg, intf.properties)
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

private fun MutableMap<String, NamingType>.buildPropertyTypes(pkg: String, property: Property) {
    buildType(pkg, property.name, property.type)
}

private fun MutableMap<String, NamingType>.buildMethodTypes(pkg: String, method: Method) {
    method.args.filter { it.direction == IN }.forEachIndexed { index, arg ->
        buildArgTypes(pkg, index, arg)
    }
    buildSingleType(pkg, method.name, method.args.filter { it.direction == OUT })
}

private fun MutableMap<String, NamingType>.buildSignalTypes(pkg: String, signal: Signal) {
    buildSingleType(pkg, signal.name, signal.args)
}

private fun MutableMap<String, NamingType>.buildArgTypes(pkg: String, index: Int, arg: Arg) {
    buildType(pkg, arg.name ?: "arg$index", arg.type)
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

/**
 * The D-Bus specification's own maximum length for a type signature. It is what bounds the
 * recursion in [buildValidatedType]: every level consumes at least one character of the signature,
 * so a signature within the limit cannot nest more than 255 deep. `dbus-daemon` enforces the same
 * number on every message it relays, so nothing a conforming service can describe is refused —
 * the longest signature in the checked-in fixtures is 15 characters.
 */
private const val MAX_SIGNATURE_LENGTH = 255

fun MutableMap<String, NamingType>.buildType(pkg: String, name: String, type: String): NamingType {
    require(type.length <= MAX_SIGNATURE_LENGTH) {
        "Type signature for '$name' is ${type.length} characters, over the D-Bus maximum of " +
            "$MAX_SIGNATURE_LENGTH"
    }
    return buildValidatedType(pkg, name, type)
}

/**
 * [buildType] once its signature is known to be within [MAX_SIGNATURE_LENGTH]. The recursive calls
 * are all to this rather than back to [buildType]: they descend into suffixes of an already
 * validated signature, so re-checking each one would be re-asserting the same fact.
 */
private fun MutableMap<String, NamingType>.buildValidatedType(
    pkg: String,
    name: String,
    type: String
): NamingType {
    (this[type] as? SimpleType)?.let { return it }
    if (type.isEmpty()) {
        return this.getOrPut(type) {
            SimpleType(type, UNIT)
        }
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
            require(type.length > 1) {
                "Array type missing element signature: $type"
            }
            if (type[1] == '{') {
                // Map
                val keyTypeStr = type.substring(2)
                val keyType = buildValidatedType(pkg, name + "Key", keyTypeStr)
                require(keyTypeStr.startsWith(keyType.type)) {
                    "Invalid type parsed out, expected $keyTypeStr but got ${keyType.type}"
                }
                val valueTypeStr = keyTypeStr.substring(keyType.type.length)
                val valueType = buildValidatedType(pkg, name + "Value", valueTypeStr)
                require(valueTypeStr.startsWith(valueType.type)) {
                    "Invalid type parsed out, expected $valueTypeStr but got ${valueType.type}"
                }
                require(
                    valueTypeStr.length > valueType.type.length &&
                        valueTypeStr[valueType.type.length] == '}'
                ) {
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
                val valueType = buildValidatedType(pkg, name + "Value", valueTypeStr)
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
                    val nextType = buildValidatedType(pkg, name + "${index++}", currentType)
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

/**
 * The naming annotation the generator understands. `qdbusxml2cpp` writes it plain on a `<property>`
 * or an `<arg>`, and suffixed `.In<n>` / `.Out<n>` on a `<method>` or `<signal>` to name the n-th
 * argument in that direction.
 *
 * Note this is the only kind of hint that can reach here: the `tp:type` /
 * `tp:name-for-bindings` attributes some introspection XML carries are XML *attributes* rather than
 * `<annotation>` elements, and the parser drops them.
 */
private const val NAMING_ANNOTATION = "org.qtproject.QtDBus.QtTypeName"

private val KOTLIN_SIMPLE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")

/**
 * Applies the naming annotations in [node] on top of the already-derived names.
 *
 * Hints are keyed by D-Bus signature like everything else in the type map, so a hint names every
 * member whose own type is that signature rather than the single member it was written on. A
 * signature nested inside a generated struct keeps its structural rendering.
 */
private fun MutableMap<String, NamingType>.applyNamingHints(
    node: XmlRootNode,
    packageOverride: String?
) {
    node.nodes.forEach { applyNamingHints(it, packageOverride) }
    for (intf in node.interfaces) {
        val pkg = packageOverride ?: intf.name.pkg
        for ((signature, name) in intf.namingHints()) {
            applyNamingHint(pkg, signature, name)
        }
    }
}

private fun MutableMap<String, NamingType>.applyNamingHint(
    pkg: String,
    signature: String,
    name: String
) {
    require(KOTLIN_SIMPLE_NAME.matches(name)) {
        "$NAMING_ANNOTATION value \"$name\" on signature $signature is not a usable Kotlin name"
    }
    val existing = this[signature] ?: error("Naming hint for unknown signature $signature")
    if (existing is GeneratedType) {
        // A struct already generates a class of its own, so the hint just renames it.
        existing.nameHint = name
    } else {
        // Anything else is a map, a list or a primitive; the hint becomes a typealias for it, so
        // the name is usable without changing the type it stands for.
        val aliased = (existing as? AliasType)?.aliased ?: existing
        this[signature] = AliasType(signature, ClassName(pkg, name.capitalCamelCase), aliased)
    }
}

/** Every `signature to hinted-name` pair the annotations of this interface carry. */
private fun Interface.namingHints(): List<Pair<String, String>> = buildList {
    for (property in properties) {
        property.annotations.namingHint()?.let { add(property.type to it) }
    }
    for (method in methods) {
        addAll(argHints(method.annotations, method.args.filter { it.direction == IN }, "In"))
        addAll(argHints(method.annotations, method.args.filter { it.direction == OUT }, "Out"))
        addAll(method.args.ownHints())
    }
    for (signal in signals) {
        addAll(argHints(signal.annotations, signal.args, "Out"))
        addAll(signal.args.ownHints())
    }
}

private fun argHints(
    annotations: List<Annotation>,
    args: List<Arg>,
    direction: String
): List<Pair<String, String>> = args.mapIndexedNotNull { index, arg ->
    annotations.hintValue("$NAMING_ANNOTATION.$direction$index")?.let { arg.type to it }
}

private fun List<Arg>.ownHints(): List<Pair<String, String>> =
    mapNotNull { arg -> arg.annotations.namingHint()?.let { arg.type to it } }

private fun List<Annotation>.namingHint(): String? = hintValue(NAMING_ANNOTATION)

private fun List<Annotation>.hintValue(name: String): String? =
    firstOrNull { it.name == name }?.value?.trim()?.takeUnless(String::isEmpty)
