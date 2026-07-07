# sdbus-kotlin

[![Build](https://github.com/Monkopedia/sdbus-kotlin/actions/workflows/arm-build-test.yaml/badge.svg)](https://github.com/Monkopedia/sdbus-kotlin/actions/workflows/arm-build-test.yaml)
[![GitHub license](https://img.shields.io/badge/license-LGPL%203-blue.svg?style=flat)](https://www.gnu.org/licenses/lgpl-3.0.txt)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/com.monkopedia/sdbus-kotlin)](https://search.maven.org/artifact/com.monkopedia/sdbus-kotlin)
[![KDoc link](https://img.shields.io/badge/API_reference-KDoc-blue)](https://monkopedia.github.io/sdbus-kotlin/)

sdbus-kotlin is a high-level D-Bus library for Kotlin Multiplatform, born as a port of
[sdbus-c++](https://github.com/Kistler-Group/sdbus-cpp). It provides typed client proxies and
service adaptors over D-Bus, and ships a Gradle plugin that generates the Kotlin bindings from
D-Bus introspection XML.

It is published for three targets, all sharing the same common API:

| Target | Backend |
| --- | --- |
| `jvm` | own D-Bus connection over [junixsocket](https://github.com/kohlschutter/junixsocket) — pure-Kotlin marshaller + dispatch, no native code |
| `linuxX64` | sd-bus via libsystemd (cinterop) |
| `linuxArm64` | sd-bus via libsystemd (cinterop) |

## API stability

**1.0 freezes the public API.** A post-0.5.0 review applied a final wave of renames and deprecations
(see the [CHANGELOG](CHANGELOG.md) migration guide), and 1.0 removes the names that were deprecated
in 0.6.0. From here the surface is stable. Compatibility is enforced in CI with
[binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator)
(JVM and klib API dumps are checked in under `api/`), so any change to the public surface is an
explicit, reviewed event.

## Choosing a backend

Application code is the same on every target — the choice is about deployment:

- **JVM**: owns its D-Bus connection over a [junixsocket](https://github.com/kohlschutter/junixsocket)
  unix-socket transport, with a pure-Kotlin marshaller and dispatcher (mirroring the native sd-bus
  backend); `junixsocket` comes in transitively with the `jvm` artifact. No native toolchain and no
  linker flags — it works anywhere a JVM and a D-Bus socket are available. Choose this when you are
  already on the JVM (server-side services, desktop apps, existing JVM codebases).
- **Native** (`linuxX64`, `linuxArm64`): wraps sd-bus directly and links against libsystemd,
  producing self-contained binaries with no JVM requirement. Choose this for small CLI tools and
  daemons, or when you want sd-bus itself doing the I/O.

A small number of fd-based entry points (`createDirectBusConnection(fd: UnixFd)`,
`createServerBusConnection(fd: UnixFd)`) are native-only; their KDoc says so explicitly.

For the full capability-by-capability contract between the two backends — including which
test suite pins each behavior and the few known divergences — see
[docs/BACKENDS.md](docs/BACKENDS.md).

# Getting Started

While sdbus-kotlin can be used directly without the generator, it can be a bit cumbersome to manage
the types and wrappers. Its recommended to use the codegenerator whenever possible.

## Code Generation

There is a gradle plugin which can generate sources as part of the the build when given D-Bus
introspection XML files, conventionally placed in `src/dbusMain`.

```
plugins {
    ...
    id("com.monkopedia.sdbus.plugin") version "1.0.0"
}

sdbus {
    sources.srcDirs("src/dbusMain")
    outputs.add("commonMain")
    generateProxies = true
    generateAdapters = true
    // optional: force generated Kotlin package
    outputPackage = "com.example.generated"
}
...
```

| Option | Meaning |
| --- | --- |
| `sources` | Directories scanned (recursively) for D-Bus introspection `*.xml` files. |
| `outputs` | Names of the Kotlin source sets that receive the generated code (defaults to `linuxMain`). Use `commonMain` to share the generated bindings across JVM and native targets. |
| `generateProxies` | Generate client-side `<Interface>Proxy` classes. |
| `generateAdapters` | Generate service-side abstract `<Interface>Adaptor` classes for you to implement. |
| `outputPackage` | Override the package of the generated code. By default it is derived from the D-Bus interface name (e.g. `org.bluez.Adapter1` generates into package `org.bluez`). |

For each `<interface>` in the XML the generator emits a Kotlin interface (e.g. `Adapter1`) plus,
depending on the flags above, an `Adapter1Proxy` and/or an abstract `Adapter1Adaptor`.

## Build Setup

To access the APIs, make sure the module depends on sdbus-kotlin. The API is a common Kotlin
module with implementations for `jvm`, `linuxX64`, and `linuxArm64`. Add the dependency to
`commonMain` to share code across targets, or to a platform source set:

```
val nativeMain by getting {
    dependencies {
       implementation("com.monkopedia:sdbus-kotlin:1.0.0")
    }
}
```

If using any complex types, their serialization is managed using kotlinx-serialization, so
that plugin must be applied as well.

```
plugins {
    kotlin("multiplatform") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
}
```

For a full example build file, see the [bluez-scan sample](samples/bluez-scan).

### Kotlin/JVM

No further setup is needed on JVM. The `jvm` artifact pulls in the junixsocket transport
transitively and connects through the standard D-Bus unix sockets — no linker flags
and no native libraries involved. The [bluez-scan sample](samples/bluez-scan) runs on JVM with:

```
$ gradle runJvm
```

### Kotlin/Native (Linux)

Since sdbus-kotlin compiles against libsystemd, your target must provide libsystemd at link and
runtime. Published artifacts do not embed a libsystemd path, so configure linker options in your
native binary.

```
linuxX64 {
    binaries {
        executable {
            linkerOpts("-lsystemd")
            // If libsystemd is outside default linker/runtime paths:
            linkerOpts("-L/usr/lib", "-Wl,-rpath,/usr/lib")
        }
    }
}
```

The bluez-scan sample runs natively with:

```
$ gradle runReleaseExecutableNative
```

## API

There are a number of `create*Connection` methods which can be used to get a connection to dbus.
If the application is only interacting with one service, it may be worth using the `withService`
to have a default service.

```
val connection = createSystemBusConnection().withService(ServiceName("org.bluez"))
```

Then the connection can be used to create proxies or adaptors as needed. Some core dbus interfaces
are provided with nicer implementations (e.g. ObjectManagerProxy). Generated proxy methods are
`suspend` functions, so call them from a coroutine:

```
runBlocking {
    val adapter = Adapter1Proxy(connection.createProxy(ObjectPath("/org/bluez/hci0")))
    adapter.startDiscovery()
}
```

Generated properties are plain Kotlin properties, each backed by a delegate that also offers
`Flow`-based observation:

```
println(adapter.name) // one-shot read

adapter.poweredProperty.values() // current value + PropertiesChanged updates
    .collect { powered -> println("Powered: $powered") }
```

(`changes()` is the same flow without the initial read.)

Without generated bindings, methods can be invoked directly; per-call timeouts are
`kotlin.time.Duration`:

```
val sum: Int = proxy.callMethodAsync(InterfaceName("org.example.Adder"), MethodName("Sum")) {
    call(listOf(1, 2, 3))
    timeout = 5.seconds
}
```

On the service side, register an object and serve an interface — either by implementing a
generated `*Adaptor` and calling its `register()`, or with the vtable DSL directly:

```
val connection = createBusConnection(ServiceName("org.example"))
val obj = createObject(connection, ObjectPath("/org/example/adder"))
obj.addVTable(InterfaceName("org.example.Adder")) {
    method(MethodName("Sum")) {
        call { numbers: List<Int> -> numbers.sum() }
    }
}
connection.startEventLoop() // serve until stopEventLoop()/release()
```

See the full [API docs](https://monkopedia.github.io/sdbus-kotlin/sdbus-kotlin/com.monkopedia.sdbus/index.html) for more information.

## Complex Types

If not using the code generator, then complex types can be defined using kotlinx-serialization.
Native types will be translated into native dbus types, and lists/maps will be automatically
handled as well.

```
@Serializable
public data class AcquireType(
  public val fd: UnixFd,
  public val mtu: UShort,
)
```

# Credits & License

sdbus-kotlin is a port of [sdbus-c++](https://github.com/Kistler-Group/sdbus-cpp) by
Stanislav Angelovič and Kistler Group, and keeps its high-level API design. Like the original,
it is licensed under the [LGPL v3](LICENSE.txt).
