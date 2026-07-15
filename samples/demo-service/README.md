# demo-service

A **server-side** sample for sdbus-kotlin. Where [`bluez-scan`](../bluez-scan) shows the
*client/proxy* half of the library, this sample shows the *adaptor/server* half: it exports an
object on the bus and lets clients call it.

The interface lives in [`src/dbusMain/DemoService.xml`](src/dbusMain/DemoService.xml) and is run
through the sdbus-kotlin codegen plugin (`generateAdapters = true`, `generateProxies = true`) to
produce `DemoService1`, `DemoService1Adaptor`, and `DemoService1Proxy` into the shared
`commonMain` source set. The interface exposes:

- a **method** `Greet(s name) -> s greeting`,
- a **read/write property** `Prefix` that emits `org.freedesktop.DBus.Properties.PropertiesChanged`
  when it is written,
- a **signal** `Tick(t count)` the service emits once per second.

[`src/commonMain/kotlin/Main.kt`](src/commonMain/kotlin/Main.kt) wires the full server lifecycle:
`createBusConnection(ServiceName)` -> `startEventLoop()` -> `addObjectManager()` ->
`createObject()` -> register the vtable -> emit ticks -> clean shutdown
(`stopEventLoop()` + releasing every resource).

> **Note on the vtable.** The sample subclasses the generated `DemoService1Adaptor` but overrides
> `register()` to bind the property with the explicit `prop { withGetter { ... }; withSetter { ... } }`
> DSL instead of the generated `with(::prefix)` property-reference binding. Binding the callbacks
> directly is what lets the setter emit `PropertiesChanged`, and it keeps value serialization
> explicit on both targets. The method and signal use the generated wiring shape unchanged.

## Dependency on the library: composite build

This sample is part of the sdbus-kotlin repository and must demonstrate the **current** API
(post-0.5.0: `startEventLoop`/`stopEventLoop`, `withGetter`/`withSetter`, typed property flows,
`Duration` timeouts, etc.). That API does **not** exist in the artifact published to Maven Central
(1.0.0). So, exactly like `bluez-scan`, [`settings.gradle.kts`](settings.gradle.kts) uses a Gradle
**composite build** (`includeBuild("../..")`). Gradle automatically substitutes the
`com.monkopedia:sdbus-kotlin` dependency declared in `build.gradle.kts` with the local source tree,
so the version coordinate there (`1.0.1`) is only a placeholder and the sample always compiles
against the library checked out next to it. No `publishToMavenLocal` step is required.

## Running

Both targets build and run from this directory.

Native (linuxX64) — a full sd-bus server:

```
$ ./gradlew runReleaseExecutableNative          # or runDevelopmentExecutableNative
```

JVM:

```
$ ./gradlew runJvm
```

The program takes an optional argument:

| args        | behaviour                                                        |
|-------------|-----------------------------------------------------------------|
| *(none)*    | run the service until `Ctrl-C`                                   |
| `<seconds>` | run the service for N seconds, then shut down cleanly           |
| `client`    | connect to a running service, call it, and print three `Tick`s |

Pass args to the gradle tasks with `--args`, e.g. `./gradlew runJvm --args="client"` or
`./gradlew runReleaseExecutableNative --args="20"`.

### No desktop session? Use `dbus-run-session`

The service uses the session bus, which may not exist on a headless box. `dbus-run-session` spins
up a throwaway one for the duration of a command:

```
# Run the native server for 20s and poke it with the native client, all on a private bus:
$ dbus-run-session -- bash -c '
    ./build/bin/native/releaseExecutable/demo-service.kexe 20 &
    sleep 2
    ./build/bin/native/releaseExecutable/demo-service.kexe client
  '
```

## Poking it with `busctl` (no extra dependencies)

With the **native** server running on a session bus, any standard tool can drive it. For example,
inside one `dbus-run-session`:

```
SVC=com.monkopedia.demo
OBJ=/com/monkopedia/demo/service
IF=com.monkopedia.demo.DemoService1

# What does it export?
busctl --user introspect $SVC $OBJ $IF
#  NAME    TYPE     SIGNATURE RESULT/VALUE FLAGS
#  .Greet  method   s         s            -
#  .Prefix property s         "Hello"      emits-change writable
#  .Tick   signal   t         -            -

# Call the method:
busctl --user call $SVC $OBJ $IF Greet s world
#  s "Hello, world!"

# Read / write the property (set-property triggers PropertiesChanged):
busctl --user get-property $SVC $OBJ $IF Prefix
busctl --user set-property $SVC $OBJ $IF Prefix s Howdy
busctl --user call $SVC $OBJ $IF Greet s world
#  s "Howdy, world!"

# Watch the periodic Tick signal and the PropertiesChanged emission live:
busctl --user monitor $SVC

# Discover the object through its ObjectManager:
busctl --user call $SVC /com/monkopedia/demo \
    org.freedesktop.DBus.ObjectManager GetManagedObjects
```

## Target notes

- **linuxX64 (native)** is backed by `sd-bus` and runs as a fully-fledged D-Bus server: external
  processes (`busctl`, a separate `client` run, a JVM client) can introspect, call methods, read/
  write properties, and receive signals over the wire.
- **JVM** is backed by an owned junixsocket connection with a pure-Kotlin marshaller and dispatcher
  (no native code). Since 0.5.0 the JVM `server` mode serves exported objects over
  the wire — external processes (`busctl`, a separate `client` run, the native client) can
  introspect, call methods, read/write properties, and receive signals from it, just like the
  native target; `./gradlew runJvm --args="client"` against the native server also works end-to-end.
  Both backends are interchangeable for serving; the remaining differences are
  same-process/direct-connection edges documented in [docs/BACKENDS.md](../../docs/BACKENDS.md).
