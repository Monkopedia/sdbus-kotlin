# sdbus-kotlin

![GitHub License](https://img.shields.io/github/license/monkopedia/sdbus-kotlin)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.0-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/com.monkopedia/kotlin-sdbus/0.1.0)](https://search.maven.org/artifact/com.monkopedia/sdbus-kotlin/0.1.0/pom)
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

The code generation tool can be found as a jar attached to the [latest
release](https://github.com/Monkopedia/sdbus-kotlin/releases).

```
java -jar codegen-all-0.1.0.jar --help
Usage: xml2kotlin [<options>] <input>

Options:
  -o, --output=<path>  Output directory
  -k, --keep           Do not delete existing content in output
  -a, --adaptor        Generate the code for an adaptor
  -p, --proxy          Generate the code for a proxy
  -h, --help           Show this message and exit
```

By default, the code generator will delete all other files in the output directory before
generating, so be sure to use `-k` if generating multiple interfaces separately.

```
$ java -jar codegen-all-0.1.0.jar -k -p -o src/nativeMain/kotlin/generated src/dbusMain/Adapter1.xml
```

## Build Setup

Currently, there is no plugin to aid in configuring gradle, so a few steps are needed to use
sdbus-kotlin. The simple part is depending on the library.

```
val nativeMain by getting {
    dependencies {
       implementation("com.monkopedia:sdbus-kotlin:0.1.0")
    }
}
```

Since sdbus-kotlin compiles against libsystemd, you have to specify some extra flags for the linker
to be successful. Make sure your system has libsystemd matching or newer than sdbus-kotlin
(currently 256.2).

```
compilerOptions {
    freeCompilerArgs.set(listOf("-linker-options", "-L /usr/lib -l systemd"))
}
```

Lastly, if using any complex types, their serialization in managed using kotlinx-serialization, so
that must be applied as well.

```
plugins {
    kotlin("multiplatform") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
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
