# Changelog

All notable changes to sdbus-kotlin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.0] - 2026-06-13

0.5.0 is the **1.0 API-freeze** release. It does two big things:

1. **Freezes the public API.** A coordinated wave of breaking changes renamed and
   reshaped the public surface so that what 0.5.0 ships is the API 1.0 will ship.
   The surface is now enforced in CI with
   [binary-compatibility-validator](https://github.com/Kotlin/binary-compatibility-validator)
   (JVM `.api` and klib `.klib.api` dumps under `api/`).
2. **Rewrites the JVM backend.** The JVM target no longer uses
   [dbus-java](https://github.com/hypfvieh/dbus-java). It now owns its D-Bus
   connection — junixsocket transport plus a pure-Kotlin marshaller and dispatcher —
   mirroring the native sd-bus backend, including cross-process serving and unix-fd
   passing. The `dbus-java` runtime dependency is gone from the published `-jvm`
   artifact (junixsocket remains).

This entry is the migration guide. Most breaking changes are renames or reshapes with
identical behavior; the old → new mappings below are what you need to update call sites.

### Breaking changes

Public async API (#57, issue #38):
- Callback-style `Proxy.callMethodAsync(MethodCall, (reply, error?) -> Unit)` — **removed** from the public API. The suspend form is now the only public async contract; use `suspend Proxy.callMethodAsync(...)`.
- `PendingAsyncCall` and the `AsyncReplyHandler` typealias — now `internal`.

Signature / error / VTable internals (#57, issues #40/#41/#42/#45):
- `createError(errNo, customMsg)` — now `internal`; construct `Error(name, message)` directly.
- The `SdbusSig` hierarchy (`PrimitiveSig`, `ListSig`, `MapSig`, `StructSig`, `InvalidSig`), all top-level `*Sig` vals, and `SerialDescriptor.asSignature` — now `internal`. `SdbusSig` itself stays public as an opaque handle; `signatureOf<A>()`, `typed<A>()`, and the `call`/`args` DSL are unchanged.
- The native cinterop `MethodCall.send(handler, scope, ULong)` overload — now `internal`.
- VTable builder item types (`MethodVTableItem`, `PropertyVTableItem`, `SignalVTableItem`, `InterfaceFlagsVTableItem`, `SignalEmitter`, `TypedMethod`, `TypedArguments`, `TypedMethodCall.SyncMethodCall`, `TypedMethodCall.AsyncMethodCall`) — no longer `data` classes (no `copy()`/`componentN()`). The `addVTable { ... }` DSL is unchanged.

Timeouts unified on `kotlin.time.Duration` (#58, issue #37):
- `Proxy.callMethod(MethodCall, ULong)` → `Proxy.callMethod(MethodCall, Duration)`.
- `suspend Proxy.callMethodAsync(MethodCall, ULong)` → `... (MethodCall, Duration)`.
- `MethodCall.send(ULong)` → `MethodCall.send(Duration)`.
- All raw `ULong`-microsecond timeout overloads are removed from the public surface. `Duration.ZERO` still means "use the connection default".

`Message` accessors are now typed `val` properties (#59, issue #44):
- `getDestination(): String?` → `val destination: BusName?`
- `getSender(): String?` → `val sender: BusName?`
- `getInterfaceName(): String?` → `val interfaceName: InterfaceName?`
- `getMemberName(): String?` → `val memberName: MemberName?`
- `getPath(): String?` → `val path: ObjectPath?`
- `getSELinuxContext()` → `val seLinuxContext`; `getCredsPid()/getCredsUid()/...` → `val credsPid/credsUid/...`
- `peekType(): Pair<Char?, String?>` → `fun peekType(): PeekedType` (named result with `type`/`contents`).
- `Signal.setDestination(String)` → `setDestination(BusName)`.

`UnixFd` and connection factories (#60, issues #48/#43):
- `UnixFd(fd, Unit)` adopt-without-dup constructor → `UnixFd.adopt(fd: Int)` factory. `UnixFd(fd)` still dups by default.
- `createServerBus(fd)` → `createServerBusConnection(fd: UnixFd)`.
- `createDirectBusConnection(fd: Int)` → `createDirectBusConnection(fd: UnixFd)`.
- `createSessionBusConnectionWithAddress(String)` → `createSessionBusConnection(address: String)` (overload).
- `createProxy(..., dontRunEventLoopThread = false)` → `createProxy(..., runEventLoopThread = true)` (flag flipped to a positive name; default behavior unchanged).

Property-delegate flows (#61, issue #46):
- `PropertyDelegate.flow()` → `values()`; `flowOrNull()` → `valuesOrNull()`. `changes()`/`changesOrNull()` are unchanged. (`values*` = current value first, then changes; `changes*` = change events only.)

Naming sweep (#62, issue #49):
- `Connection.enterEventLoopAsync()` → `startEventLoop()`; `suspend leaveEventLoop()` → `suspend stopEventLoop()`.
- `Connection.getUniqueName()` → `val uniqueName`; `getMethodCallTimeout()/setMethodCallTimeout(Duration)` → `var methodCallTimeout`. (Remote calls such as `PeerProxy.getMachineId()`, `PropertiesProxy.getAll()`, `ObjectManagerProxy.getManagedObjects()`, and property `get`/`set` stay methods.)
- Duplicate top-level/companion `DBUS_PROPERTIES_INTERFACE_NAME` constants removed; the canonical home is `PropertiesProxy.INTERFACE_NAME`.
- `PropertiesProxy.onPropertiesChanged` hook removed; supply the callback to `registerPropertiesProxy(onPropertiesChanged = ...)` instead.

Platform-specific surface (#87, issue #82):
- `createRemoteSystemBusConnection` is now **native-only** (removed from the JVM/common API, where it was a no-op-ish mis-declaration).

### Added

- `UnixFd.adopt(fd: Int)` — take ownership of a descriptor without dup'ing (#60).
- `PeekedType` — named result type for `Message.peekType()` (#59).
- `PropertyDelegate.values()` / `valuesOrNull()` (#61).
- **JVM owned D-Bus connection** (issue #93, landed across phases #94–#103): pure-Kotlin
  byte marshaller (#94), junixsocket transport with SASL EXTERNAL framing (#95), client
  path (#96), wire signal emission (#99), cross-process serving of methods / properties /
  introspection (#100), and SCM_RIGHTS unix-fd passing (#102). Phase 6 (#103) made the
  owned connection the JVM backend and retired dbus-java.
- Independent-peer test coverage against [python-dbusmock](https://github.com/martinpitt/python-dbusmock) (issues #70/#73/#75/#76) and a server-side sample, `samples/demo-service` (#88).
- Documentation: a backend parity matrix at `docs/BACKENDS.md` (#79, #91) and a rewritten README (#77).

### Behavior changes

- **JVM backend no longer depends on dbus-java** (issue #93). The published `-jvm`
  artifact drops the `dbus-java-core` / `dbus-java-transport-junixsocket` runtime
  dependencies; junixsocket remains. Application code is unchanged.
- JVM strict reply deserialization, call-after-release now throws, and errno → D-Bus
  error-name mapping pinned to native output, for JVM ↔ native parity (#63, issue #56).
- JVM honors `Connection.methodCallTimeout` and now throws when the bus is unreachable
  instead of silently falling back to an in-process stub (#85, issues #80/#81).

### Fixed

- JVM wire fixes: struct marshalling, foreign (non-sdbus) error names, and grouped /
  multi-out replies now round-trip correctly against real peers (#78, issues #71/#72/#74).
- Native heap-corruption race fixed by taking the `SdBus` lock in
  `sd_bus_message_new_method_return` — root cause of the long-standing ARM async flake
  (#86, issue #84).
- Generated adaptor property getters now serialize the value, so a remote
  `Properties.Get` works on native (#104, issue #89).

## [0.4.5] - 2026-06-03

- Upgrade to Kotlin 2.4.0.
- Auto-release Maven Central publications.

## [0.4.4] - 2026-06-02

- Fix JVM empty-collection argument signature and `ay` → `UByte` deserialization.
- Pin `jvmTarget` to 17 so the published JVM artifact stays consumable by JVM-17 projects.

## [0.4.3] - 2026-04-16

- Rename codegen Maven coordinates to `com.monkopedia:sdbus-kotlin-codegen`.
- Wire generator tasks as dependencies of the Kotlin compile and Jar tasks.
- Re-box unsigned primitives in `ListEncoder`.

## [0.4.2] - 2026-03-01

- Use KotlinPoet for generated call parameters; fix keyword escaping in generated
  adaptor signal calls.

## [0.4.1] - 2026-02-22

- Native async/event-loop stability: run the event loop on a dedicated dispatcher,
  serialize async slot unref through the `SdBus` lock, and fix several startup/exit races
  (including the ARM async integration test).

## [0.4.0] - 2026-02-22

- Add the Kotlin/JVM backend alongside Kotlin/Native, sharing one common API.
- Add cross-runtime interop and stress test modules.
- Codegen: package override support and stronger generation tests.

[0.5.0]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.5.0
[0.4.5]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.5
[0.4.4]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.4
[0.4.3]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.3
[0.4.2]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.2
[0.4.1]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.1
[0.4.0]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.0
