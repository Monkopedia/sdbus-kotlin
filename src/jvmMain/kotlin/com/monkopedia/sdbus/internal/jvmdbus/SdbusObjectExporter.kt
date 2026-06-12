package com.monkopedia.sdbus.internal.jvmdbus

import com.monkopedia.sdbus.InterfaceName
import com.monkopedia.sdbus.Message
import com.monkopedia.sdbus.MethodCall
import com.monkopedia.sdbus.ObjectPath
import com.monkopedia.sdbus.PropertyName
import com.monkopedia.sdbus.Variant
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicLong
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.annotation.AnnotationDescription
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.DynamicType
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusMemberName
import org.freedesktop.dbus.connections.AbstractConnection
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.ObjectManager
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant as JavaVariant

/**
 * Issue #90, approach A: makes JVM-hosted server objects reachable cross-process.
 *
 * dbus-java only dispatches incoming method/property/ObjectManager calls to objects that were
 * registered through its native `AbstractConnection.exportObject(path, DBusInterface)` -- a
 * reflection-driven path that requires a statically typed [DBusInterface] whose Java method
 * parameter/return types marshal to the right D-Bus signatures. sdbus-kotlin's vtable is dynamic
 * (signatures + callbacks resolved at runtime), so there is no compile-time interface to export.
 *
 * This bridge closes that gap: for each exported object it synthesizes, at runtime via ByteBuddy,
 * a [DBusInterface] subtype per D-Bus interface whose methods carry parameter/return types that
 * dbus-java's `Marshalling` turns back into the exact wire signatures the vtable declares, then
 * exports a [java.lang.reflect.Proxy] implementing those interfaces (plus [Properties] and
 * [ObjectManager] when the object serves them). Incoming calls land in [SdbusInvocationHandler],
 * which converts dbus-java's deserialized Java values into the sdbus value model, routes through
 * the very same in-process [JvmStaticDispatch] table the vtable already populates (so methods,
 * properties and ObjectManager.GetManagedObjects all reuse the existing, tested handlers), and
 * converts the reply back. No second dispatch implementation; dbus-java owns the wire codec.
 *
 * The bridge is strictly additive and best-effort: it never alters the in-process short-circuit
 * path, and any interface/method whose signature it cannot yet map (e.g. structs, multi-out
 * returns, type signatures) is simply left un-exported rather than failing the registration.
 */
internal class SdbusObjectExporter(
    private val connection: AbstractConnection,
    private val objectPath: String,
    private val dispatchDestination: String
) {
    private var exported = false
    private var lastSignature: ExportSignature? = null

    private data class ExportSignature(
        val interfaces: Map<String, List<ExportedMethodSpec>>,
        val serveProperties: Boolean,
        val serveObjectManager: Boolean
    )

    data class ExportedMethodSpec(
        val member: String,
        val inputSignature: String,
        val outputSignature: String
    )

    /**
     * Recomputes and (re-)exports the dbus-java object for the current vtable state. Cheap no-op
     * when nothing relevant changed. Failures are swallowed -- export is an enhancement over the
     * in-process path, never a precondition for it.
     */
    @Synchronized
    fun update(
        interfaces: Map<String, List<ExportedMethodSpec>>,
        serveProperties: Boolean,
        serveObjectManager: Boolean
    ) {
        val signature = ExportSignature(interfaces, serveProperties, serveObjectManager)
        if (signature == lastSignature && exported) return
        runCatching { unexportIfNeeded() }
        if (interfaces.isEmpty() && !serveProperties && !serveObjectManager) {
            lastSignature = signature
            return
        }
        runCatching {
            val proxy = buildProxy(interfaces, serveProperties, serveObjectManager)
            connection.exportObject(objectPath, proxy)
            exported = true
            lastSignature = signature
        }.onFailure {
            exported = false
        }
    }

    @Synchronized
    fun close() {
        runCatching { unexportIfNeeded() }
        lastSignature = null
    }

    private fun unexportIfNeeded() {
        if (exported) {
            connection.unExportObject(objectPath)
            exported = false
        }
    }

    private fun buildProxy(
        interfaces: Map<String, List<ExportedMethodSpec>>,
        serveProperties: Boolean,
        serveObjectManager: Boolean
    ): DBusInterface {
        val specsByKey = mutableMapOf<Pair<String, String>, ExportedMethodSpec>()
        val unloaded = mutableListOf<DynamicType.Unloaded<*>>()
        val generatedInterfaceNames = mutableListOf<String>()

        interfaces.forEach { (ifaceName, methods) ->
            val exportable = methods.filter { spec ->
                runCatching { validateExportable(spec) }.getOrDefault(false)
            }
            if (exportable.isEmpty()) return@forEach
            val generated = generateInterface(ifaceName, exportable)
            unloaded += generated.unloaded
            generatedInterfaceNames += generated.className
            exportable.forEach { spec -> specsByKey[ifaceName to spec.member] = spec }
        }

        val loader = if (unloaded.isEmpty()) {
            BRIDGE_CLASS_LOADER
        } else {
            val first = unloaded.first()
            val combined = unloaded.drop(1).fold(first) { acc, next -> acc.include(next) }
            combined.load(BRIDGE_CLASS_LOADER, ClassLoadingStrategy.Default.WRAPPER)
                .loaded.classLoader
        }

        val proxyInterfaces = buildList<Class<*>> {
            generatedInterfaceNames.forEach { name -> add(Class.forName(name, true, loader)) }
            if (serveProperties) add(Properties::class.java)
            if (serveObjectManager) add(ObjectManager::class.java)
            if (isEmpty()) add(DBusInterface::class.java)
        }

        val handler = SdbusInvocationHandler(objectPath, dispatchDestination, specsByKey)
        return Proxy.newProxyInstance(
            loader,
            proxyInterfaces.toTypedArray(),
            handler
        ) as DBusInterface
    }

    private data class GeneratedInterface(
        val className: String,
        val unloaded: DynamicType.Unloaded<*>
    )

    private fun generateInterface(
        ifaceName: String,
        methods: List<ExportedMethodSpec>
    ): GeneratedInterface {
        val className = "${GENERATED_PACKAGE}.SdbusIface\$${NEXT_ID.getAndIncrement()}"
        var builder: DynamicType.Builder<*> = ByteBuddy()
            .makeInterface(DBusInterface::class.java)
            .name(className)
            .annotateType(
                AnnotationDescription.Builder.ofType(DBusInterfaceName::class.java)
                    .define("value", ifaceName)
                    .build()
            )
        methods.forEach { spec ->
            val paramTypes = splitTopLevelTypes(spec.inputSignature).map(::signatureToGeneric)
            val outputTypes = splitTopLevelTypes(spec.outputSignature)
            val returnType: TypeDescription.Generic = when (outputTypes.size) {
                0 -> TypeDescription.ForLoadedType.of(Void.TYPE).asGenericType()
                else -> signatureToGeneric(outputTypes.first())
            }
            builder = builder
                .defineMethod(javaMethodName(spec.member), returnType, Visibility.PUBLIC)
                .withParameters(paramTypes)
                .withoutCode()
                .annotateMethod(
                    AnnotationDescription.Builder.ofType(DBusMemberName::class.java)
                        .define("value", spec.member)
                        .build()
                )
        }
        return GeneratedInterface(className, builder.make())
    }

    // Only export methods whose entire input signature and (single) output signature map to
    // round-trippable Java types. Structs, multi-out returns and 'g' (signature) args are not
    // yet supported and leave the method un-exported instead of breaking registration.
    private fun validateExportable(spec: ExportedMethodSpec): Boolean {
        val outputs = splitTopLevelTypes(spec.outputSignature)
        if (outputs.size > 1) return false
        (splitTopLevelTypes(spec.inputSignature) + outputs).forEach { signatureToGeneric(it) }
        return true
    }

    companion object {
        private const val GENERATED_PACKAGE = "com.monkopedia.sdbus.generated"
        private val NEXT_ID = AtomicLong(1)
        private val BRIDGE_CLASS_LOADER: ClassLoader = DBusInterface::class.java.classLoader

        private fun javaMethodName(member: String): String = if (member.isNotEmpty() &&
            member[0].isLetter() &&
            member.all { it.isLetterOrDigit() || it == '_' }
        ) {
            member
        } else {
            "m_${member.hashCode().toUInt()}"
        }
    }
}

/**
 * Maps a single complete D-Bus type to the Java type whose dbus-java `Marshalling.getDBusType`
 * inverse produces exactly that signature. Throws [UnsupportedSignatureException] for types not
 * yet bridged so the caller can skip them.
 */
private class UnsupportedSignatureException(signature: String) :
    RuntimeException("Unsupported D-Bus signature for JVM export: $signature")

private fun signatureToGeneric(signature: String): TypeDescription.Generic = when {
    signature == "b" -> rawGeneric(Boolean::class.javaObjectType)
    signature == "y" -> rawGeneric(Byte::class.javaObjectType)
    signature == "n" -> rawGeneric(Short::class.javaObjectType)
    signature == "q" -> rawGeneric(org.freedesktop.dbus.types.UInt16::class.java)
    signature == "i" -> rawGeneric(Int::class.javaObjectType)
    signature == "u" -> rawGeneric(org.freedesktop.dbus.types.UInt32::class.java)
    signature == "x" -> rawGeneric(Long::class.javaObjectType)
    signature == "t" -> rawGeneric(org.freedesktop.dbus.types.UInt64::class.java)
    signature == "d" -> rawGeneric(Double::class.javaObjectType)
    signature == "s" -> rawGeneric(String::class.java)
    signature == "o" -> rawGeneric(DBusPath::class.java)
    signature == "h" -> rawGeneric(org.freedesktop.dbus.FileDescriptor::class.java)
    signature == "v" -> rawGeneric(JavaVariant::class.java)
    signature.startsWith("a{") && signature.endsWith("}") -> {
        val entry = splitTopLevelTypes(signature.substring(2, signature.length - 1))
        if (entry.size != 2) throw UnsupportedSignatureException(signature)
        TypeDescription.Generic.Builder.parameterizedType(
            TypeDescription.ForLoadedType.of(Map::class.java),
            listOf(signatureToGeneric(entry[0]), signatureToGeneric(entry[1]))
        ).build()
    }
    signature.startsWith("a") && signature.length > 1 -> {
        TypeDescription.Generic.Builder.parameterizedType(
            TypeDescription.ForLoadedType.of(List::class.java),
            listOf(signatureToGeneric(signature.substring(1)))
        ).build()
    }
    else -> throw UnsupportedSignatureException(signature)
}

private fun rawGeneric(cls: Class<*>): TypeDescription.Generic =
    TypeDescription.ForLoadedType.of(cls).asGenericType()

/**
 * Routes an incoming dbus-java method/property/ObjectManager call (already deserialized into Java
 * values by dbus-java) into the in-process [JvmStaticDispatch] table the vtable populates, then
 * converts the reply back into the Java value model dbus-java will marshal onto the wire.
 */
private class SdbusInvocationHandler(
    private val objectPath: String,
    private val dispatchDestination: String,
    private val specsByKey: Map<Pair<String, String>, SdbusObjectExporter.ExportedMethodSpec>
) : InvocationHandler {

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        val arguments = args?.toList().orEmpty()
        return when {
            method.declaringClass == Any::class.java -> handleObjectMethod(proxy, method, arguments)
            method.name == "getObjectPath" && arguments.isEmpty() -> objectPath
            method.name == "isRemote" && arguments.isEmpty() -> false
            method.declaringClass == Properties::class.java -> handleProperties(
                method.name,
                arguments
            )
            method.declaringClass == ObjectManager::class.java -> handleGetManagedObjects()
            else -> handleUserMethod(method, arguments)
        }
    }

    private fun handleObjectMethod(proxy: Any, method: Method, args: List<Any?>): Any? =
        when (method.name) {
            "toString" -> "SdbusExportedObject($objectPath)"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args.firstOrNull()
            else -> null
        }

    private fun handleUserMethod(method: Method, args: List<Any?>): Any? {
        val ifaceName = method.declaringClass.getAnnotation(DBusInterfaceName::class.java)?.value
            ?: throw DBusExecutionException("Missing interface name for ${method.name}")
        val member = method.getAnnotation(DBusMemberName::class.java)?.value ?: method.name
        val spec = specsByKey[ifaceName to member]
            ?: throw DBusExecutionException("Unknown method $ifaceName.$member")
        val inputTypes = splitTopLevelTypes(spec.inputSignature)
        val sdbusArgs = args.mapIndexed { index, value ->
            fromJavaWireValue(value, inputTypes.getOrNull(index))
        }
        val reply = dispatch(ifaceName, member, sdbusArgs)
        val outputTypes = splitTopLevelTypes(spec.outputSignature)
        return when (outputTypes.size) {
            0 -> null
            else -> sdbusToJava(reply.firstOrNull(), outputTypes.first())
        }
    }

    private fun handleProperties(name: String, args: List<Any?>): Any? = when (name) {
        "Get" -> {
            val result = invokeDispatch(
                PROPERTIES_INTERFACE,
                "Get",
                listOf(args[0].toString(), args[1].toString())
            )
            (result as? Variant)?.let(::sdbusVariantToJava)
        }
        "GetAll" -> {
            val result = invokeDispatch(
                PROPERTIES_INTERFACE,
                "GetAll",
                listOf(args[0].toString())
            )
            @Suppress("UNCHECKED_CAST")
            (result as? Map<PropertyName, Variant>).orEmpty().entries.associate { (key, value) ->
                key.value to sdbusVariantToJava(value)
            }
        }
        "Set" -> {
            val newValue = fromJavaSignalValue(args[2])
            invokeDispatch(
                PROPERTIES_INTERFACE,
                "Set",
                listOf(args[0].toString(), args[1].toString(), newValue)
            )
            null
        }
        else -> throw DBusExecutionException("Unsupported Properties method $name")
    }

    private fun handleGetManagedObjects(): Map<DBusPath, Map<String, Map<String, JavaVariant<*>>>> {
        val result = invokeDispatch(
            OBJECT_MANAGER_INTERFACE,
            "GetManagedObjects",
            emptyList()
        )

        @Suppress("UNCHECKED_CAST")
        val managed = (result as? Map<ObjectPath, Map<InterfaceName, Map<PropertyName, Variant>>>)
            .orEmpty()
        return managed.entries.associate { (path, ifaces) ->
            DBusPath(path.value) to ifaces.entries.associate { (iface, props) ->
                iface.value to props.entries.associate { (prop, variant) ->
                    prop.value to sdbusVariantToJava(variant)
                }
            }
        }
    }

    // Routes through the same JvmStaticDispatch table the in-process path uses, mirroring
    // PureJavaDbusProxy.callStaticDispatch's reply extraction so a method's async reply, error
    // reply or direct return value are all handled identically.
    private fun dispatch(interfaceName: String, methodName: String, args: List<Any?>): List<Any?> {
        val result = invokeDispatch(interfaceName, methodName, args)
        return when (result) {
            Unit -> emptyList()
            is JvmStaticDispatch.DispatchResult -> {
                result.reply.error?.let { throw DBusExecutionException(it.message ?: it.name) }
                result.reply.payload.toList()
            }
            else -> listOf(result)
        }
    }

    private fun invokeDispatch(interfaceName: String, methodName: String, args: List<Any?>): Any? {
        if (!JvmStaticDispatch.hasHandler(
                objectPath = objectPath,
                interfaceName = interfaceName,
                methodName = methodName,
                args = args,
                destination = dispatchDestination
            )
        ) {
            throw DBusExecutionException(
                "No binding for $objectPath:$interfaceName.$methodName/${args.size}"
            )
        }
        val inbound = MethodCall().also {
            it.metadata = Message.Metadata(
                interfaceName = interfaceName,
                memberName = methodName,
                destination = dispatchDestination,
                path = objectPath,
                valid = true,
                empty = args.isEmpty()
            )
            it.payload.addAll(args)
        }
        return JvmCurrentMessageContext.withMessage(inbound) {
            JvmStaticDispatch.invokeOrNull(
                objectPath = objectPath,
                interfaceName = interfaceName,
                methodName = methodName,
                args = args,
                destination = dispatchDestination
            )
        }
    }

    companion object {
        private const val PROPERTIES_INTERFACE = "org.freedesktop.DBus.Properties"
        private const val OBJECT_MANAGER_INTERFACE = "org.freedesktop.DBus.ObjectManager"
    }
}

private fun sdbusVariantToJava(variant: Variant): JavaVariant<*> {
    val payload = extractVariantPayload(variant)
    return toJavaVariantValue(payload.signature, payload.value)
}

// Signature-driven conversion of an sdbus reply value into the exact Java type dbus-java will
// marshal for that signature. Reuses toJavaSignalValue for the unambiguous leaf cases and only
// overrides where the wire signature -- not the value shape -- decides the Java type (object
// paths, and recursion into containers so element/entry signatures stay authoritative).
private fun sdbusToJava(value: Any?, signature: String): Any? = when {
    value == null -> null
    signature == "o" -> DBusPath(
        when (value) {
            is ObjectPath -> value.value
            else -> value.toString()
        }
    )
    signature.startsWith("a{") && signature.endsWith("}") -> {
        val entry = splitTopLevelTypes(signature.substring(2, signature.length - 1))
        val map = value as? Map<*, *> ?: emptyMap<Any?, Any?>()
        map.entries.associate { (k, v) ->
            sdbusToJava(k, entry.getOrNull(0).orEmpty()) to
                sdbusToJava(v, entry.getOrNull(1).orEmpty())
        }
    }
    signature.startsWith("a") && signature.length > 1 -> {
        val element = signature.substring(1)
        (wireValueAsList(value) ?: emptyList()).map { sdbusToJava(it, element) }
    }
    else -> toJavaSignalValue(value)
}
