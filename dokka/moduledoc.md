# Module sdbus-kotlin

sdbus-kotlin is a high-level [D-Bus](https://www.freedesktop.org/wiki/Software/dbus/) library
for Kotlin Multiplatform, born as a port of
[sdbus-c++](https://github.com/Kistler-Group/sdbus-cpp). It provides typed client proxies and
service adaptors over D-Bus, plus a Gradle plugin that generates the Kotlin bindings from D-Bus
introspection XML.

The same common API is published for three targets:

| Target | Backend |
| --- | --- |
| `jvm` | An owned D-Bus connection — [junixsocket](https://github.com/kohlschutter/junixsocket) transport with a pure-Kotlin marshaller and dispatcher, no native code (since 0.5.0) |
| `linuxX64` | sd-bus via libsystemd (cinterop) |
| `linuxArm64` | sd-bus via libsystemd (cinterop) |

Application code is identical on every target — the choice of backend is a deployment decision.
For the capability-by-capability contract between the JVM and native backends, including which
test suite pins each behavior and the few known divergences, see
[docs/BACKENDS.md](https://github.com/Monkopedia/sdbus-kotlin/blob/main/docs/BACKENDS.md).

## Where to start

The public API flows from a connection to typed objects:

1. **Open a connection.** Use one of the `create…BusConnection` factories — for example
   `createSystemBusConnection()`, `createSessionBusConnection()`, or the direct/server fd
   factories on native. The result is a [Connection][com.monkopedia.sdbus.Connection], which
   owns the I/O event loop (`startEventLoop()` / `stopEventLoop()`).
2. **Get a proxy or export an object.** `createProxy(...)` returns a
   [Proxy][com.monkopedia.sdbus.Proxy] for talking to a remote service; `createObject(...)`
   returns an [Object][com.monkopedia.sdbus.Object] you register interfaces on with the
   `addVTable { ... }` DSL to serve calls, properties, and signals.
3. **Make typed calls.** Issue method calls via the `call`/`args` DSL (synchronous or
   `suspend`), read and write properties through property delegates
   (`values()` / `changes()` flows for observation), and subscribe to signals with
   `signalFlow`. Names are wrapped in value classes — `BusName`, `InterfaceName`,
   `MemberName`, `ObjectPath`, `PropertyName` — and timeouts use `kotlin.time.Duration`.

Most users do not call this surface by hand: the **codegen Gradle plugin**
(`com.monkopedia.sdbus.plugin`) generates the proxy and adaptor types from introspection XML
(conventionally placed in `src/dbusMain`), giving you fully typed interfaces to implement and
consume.

For runnable quick-starts — Gradle setup for JVM and native, code generation, and complex-type
handling — see the
[README](https://github.com/Monkopedia/sdbus-kotlin/blob/main/README.md). For a worked
server-side example, see [`samples/demo-service`](https://github.com/Monkopedia/sdbus-kotlin/tree/main/samples/demo-service).

## API stability

**1.0 freezes the public API** — a post-0.5.0 review applied a final wave of renames and
deprecations, and 1.0 removes the names that were deprecated in 0.6.0. Compatibility is enforced
in CI with
[binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator)
(JVM and klib API dumps are checked in under `api/`), so any change to the public surface is an
explicit, reviewed event. The per-release migration notes are in the
[CHANGELOG](https://github.com/Monkopedia/sdbus-kotlin/blob/main/CHANGELOG.md).
