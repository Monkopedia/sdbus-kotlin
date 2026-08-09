# Changelog

All notable changes to sdbus-kotlin are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project aims to follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added — codegen

- **Generated code now carries KDoc taken from the introspection XML.** Documentation reaches the
  generated interface, proxy and adaptor from both carriers the parser already understood and
  previously discarded: `org.gtk.GDBus.DocString` / `DocString.Short` annotations, and `<doc:doc>`
  elements of the freedesktop doc DTD. Argument documentation becomes `@param`/`@return` tags.
  Regenerating from unchanged XML adds comments and nothing else — no signature, type name or
  emitted statement changes — so this is additive documentation rather than a behavior change.
  XML that documents nothing generates exactly what it did before. (#160)

### Changed — codegen

- **Standard D-Bus annotations are now mapped onto the runtime vtable/proxy flags.** The generated
  adaptor carries `org.freedesktop.DBus.Deprecated` (on the interface, methods, signals and
  properties) and `org.freedesktop.DBus.Property.EmitsChangedSignal` into its `addVTable` block, and
  both generators honor `org.freedesktop.DBus.Method.NoReply` — the adaptor registers the method with
  `hasNoReply = true` and the proxy calls it with `dontExpectReply = true`. Previously all three
  annotations were parsed and then dropped, so nothing in the generated code carried them at all.
  (#159)
- **How much of that reaches the introspection a running adaptor *serves* depends on the backend, and
  it is not yet a full round-trip of the source XML.** Measured against a live adaptor on both
  backends via `org.freedesktop.DBus.Introspectable.Introspect`:
  `Deprecated` on a **method, signal or property** is served on **both** backends; `Deprecated` on the
  **interface** and `EmitsChangedSignal` (`const` / `invalidates` / `false`) are served on **native
  only** — the JVM backend reads no vtable flags beyond the per-member deprecated bit; and
  `org.freedesktop.DBus.Method.NoReply` is served by **neither** backend. This affects only what an
  adaptor advertises to external introspectors (`busctl`, d-feet, other language bindings), not how it
  behaves. Tracked in #193. (#159)
- `Deprecated` is advisory: where it is served, it changes only the introspection metadata the
  adaptor publishes. `EmitsChangedSignal` is declarative property-update behavior — values other
  than the D-Bus default (`invalidates`, `const`, `false`) now register the matching
  `Flags.PropertyUpdateBehaviorFlags` entry. It previously reached the generator only to decide
  whether a *writable* property got a `notifying` delegate, so read-only properties dropped it
  entirely. Neither annotation changes how a method is called. (#159)
- **`Method.NoReply` is a behavior change for consumers who regenerate from unchanged XML.** If your
  XML already declares it on a method, regenerating switches that method to fire-and-forget on both
  sides: the adaptor sends no reply and the proxy awaits none, so failures no longer propagate back
  to the caller. Before this change the annotation was silently ignored and both sides used ordinary
  request/reply semantics. Drop the annotation from the XML if you were relying on that. (#159)

### Changed — dependencies

- **xmlutil** is now `implementation`-scoped rather than `api`-scoped in `sdbus-kotlin-codegen`. It is
  used only inside the introspection-XML parser and appears in zero public signatures, so it no longer
  takes part in consumers' compile-classpath resolution — it stays on the runtime classpath, where the
  tool actually needs it. The binary-compatibility surface is unchanged. This is a small source-breaking
  change for anyone who relied on xmlutil being transitively available at compile time through
  `sdbus-kotlin-codegen` or the `com.monkopedia.sdbus.plugin` Gradle plugin; declare it directly instead.
  (#167)

### Fixed — codegen

- **A signal with exactly one argument of a parameterized type no longer generates code that fails
  to compile.** The proxy's signal decoder was selected from the *kind* of the mapped Kotlin type
  rather than from how many arguments the signal declares, so a single argument of an array or dict
  type (`as`, `ai`, `a{sv}` — ordinary shapes in real interfaces) fell through to the
  constructor-reference branch and emitted `call(::List<String>)`, which is not valid Kotlin. The
  same fall-through gave a single argument of a *struct* type `call(::Entry)`, which compiled but
  decoded two top-level arguments where the adaptor registers one; that is corrected too. Signals
  with no arguments and with several arguments are unaffected, and regenerating XML that declares
  neither shape produces byte-identical output. A `SignalArgShapesTest` fixture now covers every
  signal argument shape, so `compile_test` guards this permanently. (#218)

### Fixed

- **`Message.credsEuid` / `Message.credsEgid` now report genuine effective ids on the JVM backend
  instead of the real ones.** The one JVM path that attaches credentials stored `getuid()` /
  `getgid()` — via `com.sun.security.auth.module.UnixSystem`, which reports only real ids — in the
  two fields named *effective*, so both answered with a plausible wrong number rather than failing.
  They are now read from the effective column of `/proc/self/status`; when that cannot be read the
  fields stay unset and the accessors throw, so a real id is never substituted for an effective one.
  Native was already correct (`SD_BUS_CREDS_EUID` / `_EGID`), so this closes a cross-backend
  divergence. No value changes on a process whose effective ids equal its real ones — every
  non-setuid process — and the public signatures are unchanged. (#247)

- **A method registered with `hasNoReply = true` now advertises
  `org.freedesktop.DBus.Method.NoReply` in the introspection the native backend serves.** The vtable
  translation tested the flag and then OR-ed the result with a literal `0u` — the constant named in
  the adjacent comment, `SD_BUS_VTABLE_METHOD_NO_REPLY`, was never bound — so the branch was a
  no-op and the bit never reached sd-bus. Behavior is unchanged: the fire-and-forget half already
  worked on both backends, and this only corrects what the adaptor tells an external introspector
  (`busctl`, d-feet, other language bindings), which previously described a fire-and-forget method
  as ordinary request/reply. The JVM backend still does not serve this annotation; that is part of
  #193. (#197)

## [1.0.1] - 2026-07-15

A maintenance release: refreshed external dependencies to their latest stable versions. There are
**no API or behavioral changes** — the public surface is byte-for-byte unchanged (both the JVM and
klib binary-compatibility dumps are identical to 1.0.0), so this is a drop-in upgrade.

### Changed — dependencies

- **Kotlin** `2.4.0` → `2.4.10` (toolchain; no klib-ABI shift).
- **kotlinx-coroutines** `1.10.2` → `1.11.0`.
- **kotlinx-atomicfu** `0.32.1` → `0.33.0`.

### Removed — dependencies

- **kotlinx-datetime** is no longer a dependency. It was declared but unused (the library uses
  `kotlin.time` from the standard library), so it has been dropped from the published artifacts —
  removing it from consumers' transitive runtime classpath. This is `implementation`-scoped and
  absent from the public API, so it does not affect the binary-compatibility surface.

### Documentation

- Describe the JVM backend by what it is (an owned junixsocket connection with a pure-Kotlin
  marshaller and dispatcher) rather than by what it no longer uses. (#152)

## [1.0.0] - 2026-07-04

1.0 **freezes the public API.** It removes the names deprecated in 0.6.0 and lands a wave of
cross-backend behavioral parity fixes so the native (sd-bus) and JVM (junixsocket wire) backends
agree on the consumer-facing surface. Every fix ships with a cross-backend regression test.

### Removed (breaking) — the 0.6.0 deprecations

- **The fluent property layer** — `AsyncPropertyGetter`, `AsyncPropertySetter`, `AllPropertiesGetter`,
  `AsyncAllPropertiesGetter`, and the single-argument `Proxy.getPropertyAsync(propertyName)` /
  `setPropertyAsync(propertyName)` / `getAllProperties()` / `getAllPropertiesAsync()` factories that
  returned them. Use the direct typed accessors instead:
  `Proxy.getPropertyAsync<T>(interfaceName, propertyName)`,
  `setPropertyAsync(interfaceName, propertyName, value)`, `getAllProperties(interfaceName)`,
  `getAllPropertiesAsync(interfaceName)`.
- **`typealias Error`** — use `SdbusException` directly.

### Fixed — cross-backend parity (JVM backend)

- **Directed signals** now unicast-route on JVM; `Signal.setDestination` was broadcast. (#138)
- **Error name**: a handler throwing a non-`SdbusException` now surfaces
  `org.freedesktop.DBus.Error.Failed` on both backends — the JVM backend previously put the
  exception message in the error-name slot, and native produced `NoReply`. This changes the
  observable `SdbusException.name` for that case. (#142)
- **ObjectManager `InterfacesAdded` and no-argument `PropertiesChanged`** now carry the object's
  current property values on JVM (were emitted with empty maps). (#143)
- **Use-after-release**: JVM connection operations now throw the same error as native after
  `release()` instead of silently succeeding. (#144)
- **`proxy.release()`** now tears down the proxy's signal handlers on JVM (was a no-op). (#145)
- **Wrong argument count** → `org.freedesktop.DBus.Error.InvalidArgs` on JVM (was `UnknownMethod`);
  a genuinely-missing member stays `UnknownMethod`. (#146)
- **Same-process `Peer` (Ping/GetMachineId) and `Introspectable` (Introspect)** calls are now
  served on JVM (the local short-circuit returned `UnknownMethod`). (#147)
- **`JvmStaticDispatch`** dispatch table made thread-safe (data race between object
  registration and concurrent dispatch). (#148)
- **`dontExpectReply`** (fire-and-forget calls) honored on JVM — no ~30s wait for a reply, and no
  error surfaced for a missing target. (#149)
- **`Connection.addMatch`** with a well-known `sender=` now receives matching signals on JVM
  (the local re-filter was dropping them). (#150)

### Added

- Serve-worker-pool saturation watchdog for non-compensated nested blocking. (#133)
- Substantial external-integration test coverage of the 0.6.0 surface and the cross-backend
  contracts, plus a coverage baseline at [`docs/TEST_COVERAGE.md`](docs/TEST_COVERAGE.md).
  (#134–#136, #139)

See [`docs/BACKENDS.md`](docs/BACKENDS.md) for the small set of same-process / direct-connection
differences that remain documented rather than matched (none on the cross-process path).

## [0.6.0] - 2026-06-18

0.6.0 is the **1.0-polish** release. A post-0.5.0 review of the public surface (epic #108)
produced a coordinated cleanup wave — renames, deprecations, and a few new first-class APIs —
so that 1.0 can ship a final, well-named surface. It also lands several real bug fixes found
along the way.

Deprecations introduced here are kept as warnings for this release and **removed at 1.0**.
(Note: the 0.5.0 changelog called itself the "1.0 API-freeze"; the post-0.5.0 review reopened a
handful of names, so 0.6.0 is the last shaping pass and **1.0** is the actual freeze.)

This entry is the migration guide. Most breaking changes are renames with identical behavior.

### Bug fixes

Memory leaks in served objects and connections (#119) — both backends:
- Every served object leaked after `release()`: the native vtable teardown never dropped the
  registered method/property/signal callbacks (which capture the adaptor), and on the JVM the
  `Properties` dispatch handlers were never unregistered from the process-wide dispatch table.
  A property-bearing served object (the common BlueZ case) leaked for the process lifetime.
- Native connections leaked once objects/signal subscriptions were registered: several cleanup
  closures captured the connection (`this@ConnectionImpl`) via member access and were retained
  by their GC cleaner. Fixed at five sites. (The compiler's non-capturing-`createCleaner` check
  cannot catch this because the capture launders through a `Reference` parameter.)

Native event-loop thread starvation (#128) — connection event loops shared one bounded 8-thread
pool, so more than 8 concurrently-running loops would starve (a served object whose loop never
got a thread could not answer calls). Each connection now gets its own dedicated loop thread.

### Breaking changes

Naming sweep (#113) — behavior-identical renames, **no compatibility aliases** (update call sites):
- `acall` → `asyncCall`, `createACall` → `createAsyncCall` (vtable method DSL; generated adaptors regenerated).
- `SdbusSig` → `TypeSignature` (in `Variant.get`/`Typed`/`signatureOf`/property accessors).
- `Message.path` → `Message.objectPath`.
- `SignalEmitter.typedMethodArguments` → `arguments`; `SignalSubscriber.methodCall` → `handler`.
- `PlainMessage.Companion.createPlainMessage()` → top-level `createPlainMessage()` (matches `createObject`/`createProxy`).
- `Flags.test(flag)` → `Flags.has(flag)`; also adds an `in` operator (`flag in flags`).
- `Connection.addMatchAsync(...)` — **removed** (had no users; the async match-install machinery is gone too). Use `addMatch`.
- `MethodReply`'s accidentally-public constructors — now `internal`.

`requestName` now reports its outcome and accepts flags (#112):
- `fun requestName(name: ServiceName)` → `fun requestName(name: ServiceName, vararg flags: RequestNameFlag): RequestNameReply`.
- New `enum RequestNameFlag { ALLOW_REPLACEMENT, REPLACE_EXISTING, DO_NOT_QUEUE }` and
  `enum RequestNameReply { PRIMARY_OWNER, IN_QUEUE, EXISTS, ALREADY_OWNER }`.
- Source-compatible: `requestName(name)` still compiles (the return is simply now ignorable). Behavior
  note: requesting a name already owned by another peer now **queues** by default and returns `IN_QUEUE`
  on both backends (the native and JVM flag handling were made consistent), rather than throwing — pass
  `DO_NOT_QUEUE` to fail fast with `EXISTS`.

fd-based connection factories are native-only (#111):
- `createDirectBusConnection(fd: UnixFd)` and `createServerBusConnection(fd: UnixFd)` are removed from the
  common (multiplatform) surface — they were `@Deprecated(level=ERROR)` JVM stubs — and are now plain
  native-only functions. Address-based `createDirectBusConnection(String)` stays common.

Mechanical reductions (#116):
- `Resource` now extends `AutoCloseable` (usable in `use { }`; `close()` delegates to `release()`).
- `Typed`, the VTable item types, etc. are no longer `data` classes (no `copy()`/`componentN()`).
- `Flags.Count`, `maybeDegrouped`, and `PropertyDelegate.name` are removed/internalized.

### Deprecations (removed at 1.0)

- `Error` → renamed `SdbusException` (#109). `Error` remains as a deprecated `typealias` (source-compatible; warns).
- The fluent property layer (#110) — `AsyncPropertyGetter`/`AsyncPropertySetter`/`AllPropertiesGetter` and their
  `onInterface`/`toValue`/`getResult` chains are deprecated in favor of the new direct accessors below.

### New API

- `SdbusException` (the renamed exception type).
- `RequestNameReply` / `RequestNameFlag` (see `requestName` above).
- Direct typed property accessors (replacing the fluent layer): `suspend Proxy.getPropertyAsync<T>(iface, prop)`,
  `suspend Proxy.setPropertyAsync<T>(iface, prop, value)`, `Proxy.getAllProperties(iface)`,
  `suspend Proxy.getAllPropertiesAsync(iface)`.
- `Object.notifying(iface, prop, initial): ReadWriteProperty` — a property delegate that emits
  `PropertiesChanged` on change (skipping no-op sets).
- `createObject(connection, objectPath, runEventLoopThread = true)` — server-side event-loop symmetry with
  `createProxy`; `startEventLoop()` is now idempotent. Source-compatible (the parameter defaults).
- `Flags.has(flag)` and the `flag in flags` operator.

### Behavioral improvements

- Generated adaptors now auto-emit `PropertiesChanged` (#115), honoring the
  `org.freedesktop.DBus.Property.EmitsChangedSignal` annotation — a remote `Set` or a server-side set both emit,
  so clients' property-change flows fire by default.
- The JVM serve worker pool is now bounded and deadlock-free under nested same-connection calls (#101),
  replacing the previously-unbounded cached thread pool.

### Docs

- KDoc cleanup (#117): removed stale sdbus-c++ doxygen (`@class`/`@c`/`std::future`/C++ examples) from the public
  surface and fixed the public dokka "couldn't resolve link" warnings.

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

[1.0.1]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v1.0.1
[1.0.0]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v1.0.0
[0.6.0]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.6.0
[0.5.0]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.5.0
[0.4.5]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.5
[0.4.4]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.4
[0.4.3]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.3
[0.4.2]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.2
[0.4.1]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.1
[0.4.0]: https://github.com/Monkopedia/sdbus-kotlin/releases/tag/v0.4.0
