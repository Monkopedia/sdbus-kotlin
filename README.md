# sdbus-kotlin

![GitHub License](https://img.shields.io/github/license/monkopedia/sdbus-kotlin)
[![Kotlin](https://img.shields.io/badge/kotlin-2.1.20-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/com.monkopedia/kotlin-sdbus/0.3.5)](https://search.maven.org/artifact/com.monkopedia/sdbus-kotlin/0.3.5/pom)
[![KDoc link](https://img.shields.io/badge/API_reference-KDoc-blue)](https://monkopedia.github.io/sdbus-kotlin/sdbus-kotlin/com.monkopedia.sdbus/index.html)

sdbus-kotlin is a direct port of sdbus-c++ to kotlin/native. Once the port completed, some
kotlinization of the APIs has begun, but is definitely not complete or API stable. sdbus-kotlin
also contains a code generator, which will generate kotlin adaptors or proxies for dbus xml
interfaces provided.

Currently sdbus-kotlin is only built for linuxX64, but the goal is to bring it to arm64 next, and
any other possible platforms after.

# Getting Started

While sdbus-kotlin can be used directly without the generator, it can be a bit cumbersome to manage
the types and wrappers. Its recommended to use the codegenerator whenever possible.

## Code Generation

There is a gradle plugin which can generate sources as part of the the build when given xml files.

```
plugins {
    ...
    id("com.monkopedia.sdbus.plugin") version "<version>"
}

sdbus {
    sources.srcDirs("src/dbusMain")
    outputs.add("linuxX64Main")
    generateProxies = true
    generateAdapters = true
    // optional: force generated Kotlin package
    outputPackage = "com.example.generated"
}
...
```

This will generate the proxies and adapters for any interfaces declared in src/dbusMain and make
them available from the `linuxX64Main` sources.

## Build Setup

To access the APIs, make sure the module depends on sdbus-kotlin. While the API is available as
a common kotlin module, its implementations are currently only for linuxX64 and linuxArm64.

```
val nativeMain by getting {
    dependencies {
       implementation("com.monkopedia:sdbus-kotlin:0.3.5")
    }
}
```

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

Lastly, if using any complex types, their serialization in managed using kotlinx-serialization, so
that must be applied as well.

```
plugins {
    kotlin("multiplatform") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}
```

For a full example build file, see the [bluez-scan sample](samples/bluez-scan).

## API

There are a number of `create*Connection` methods which can be used to get a connection to dbus.
If the application is only interacting with one service, it may be worth using the `withService`
to have a default service.

```
val connection = createSystemBusConnection().withService(ServiceName("org.bluez"))
```

Then the connection can be used to create proxies or adaptors as needed. Some core dbus interfaces
are provided with nicer implementations (e.g. ObjectManagerProxy).

```
val adapter = Adapter1Proxy(connection.createProxy(ObjectPath("/org/bluez/hci0")))
adapter.startDiscovery()
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
