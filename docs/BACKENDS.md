# JVM ↔ native backend parity matrix

sdbus-kotlin ships one common API with two implementations: the **native** backend
(`linuxX64`/`linuxArm64`, sd-bus via libsystemd) and the **JVM** backend, which owns its D-Bus
connection over a [junixsocket](https://github.com/kohlschutter/junixsocket) transport with a
pure-Kotlin marshaller and dispatcher (no native code — junixsocket is the only transitive
dependency). This document is the contract for what behaves identically on both
backends and where they differ, as of the frozen 1.0 API surface.

Every row below is pinned by tests on current `main`, not by intention:

- **`src/commonTest`** suites compile and run against *every* target, so any assertion there is
  parity by construction — a backend divergence shows up as a per-target test failure.
- **`cross_test/src/commonTest`** suites additionally drive both backends against
  [python-dbusmock](https://github.com/martinpitt/python-dbusmock), an *independent*
  Python/GDBus peer, which catches symmetric serializer bugs that own-server round-trips
  cannot. CI runs both under `dbus-run-session` with `python3-dbusmock` installed
  (`.github/workflows/arm-build-test.yaml`).
- Backend-specific behavior is pinned by per-backend suites (`src/jvmTest`, `src/nativeTest`).

## Summary matrix

| Capability | JVM | Native | Notes |
| --- | --- | --- | --- |
| Method calls: sync (`callMethod`), suspend (`callMethodAsync`), callback async + `PendingAsyncCall` | ✅ | ✅ | Identical semantics incl. cancel/isPending |
| Struct (`@Serializable`) marshalling to/from remote peers | ✅ | ✅ | JVM fixed in #71 |
| Multi-out (grouped) replies (`isGroupedReturn`) | ✅ | ✅ | JVM fixed in #74 |
| Per-call timeout (`timeout = …` in the invoker block) | ✅ | ✅ | Timeout *error name* may differ, see below |
| Connection-level `Connection.methodCallTimeout` | ✅ | ✅ | Applied as the default for calls without an explicit timeout; JVM fixed in #80 |
| Error propagation: named D-Bus errors round-trip name + message verbatim | ✅ | ✅ | Incl. foreign (non-sdbus) peers; JVM fixed in #72 |
| errno → D-Bus error-name mapping for locally created errors | ✅ | ✅ | JVM pinned to native output |
| Call-after-release fails with `IllegalArgumentException("Connection has already been released")` | ✅ | ✅ | |
| `requestName(name, vararg flags)` → `RequestNameReply` | ✅ | ✅ | Native/JVM flag handling made consistent (#112): a name owned by another peer **queues** (`IN_QUEUE`) by default; pass `DO_NOT_QUEUE` to fail fast (`EXISTS`) |
| Properties: `Get`/`Set`/`GetAll`, property delegates (`values()`/`changes()`) — client **and** server | ✅ | ✅ | Verified as a client and as a server on both backends; JVM *serving* `Get`/`Set`/`GetAll` to an external peer now works over the wire (#90 closed) — see "Server-side object export" |
| `PropertiesChanged` emission incl. getter resolution | ✅ | ✅ | JVM fixed in #28; emission reaches external subscribers (pinned by `WireSignalEmissionExternalTest`); generated adaptors auto-emit honoring `EmitsChangedSignal` (#115) |
| Signals: emission, subscription, `signalFlow` | ✅ | ✅ | Emission and subscription/`signalFlow` both cross-process-verified; JVM external emission pinned by `WireSignalEmissionExternalTest` |
| ObjectManager: `GetManagedObjects`, `InterfacesAdded`/`Removed`, `ObjectManagerProxy` flows — client **and** server | ✅ | ✅ | Verified as a client and as a server; JVM *serving* `GetManagedObjects` to an external peer now works over the wire (#90 closed) — see "Server-side object export" |
| **Serving exported objects to external processes** (incoming method calls, `Properties.Get`/`Set`/`GetAll`, `GetManagedObjects`) | ✅ | ✅ | **#90 closed (0.5.0):** the JVM backend serves incoming calls over its owned wire connection (`WireServe`), so an external client (busctl, another process) reaches a JVM-exported object exactly as on native — see "Server-side object export". Same-JVM calls still take an in-process shortcut (`JvmStaticDispatch`) |
| `UnixFd` type semantics (dup constructor vs `adopt`, `release` closes) | ✅* | ✅ | *JVM dup needs junixsocket native support |
| Unix FD passing over the wire | ⚠️ untested | ✅ | JVM has the conversion path but no independent-peer test |
| Connection factories (11 total) | 9 of 11 | 11 of 11 | fd-based factories are native-only |
| Behavior when the bus is unreachable | ✅ throws `Error` | ✅ throws `Error` | JVM fixed in #81; the in-process stub backend is an explicit internal test opt-in only |
| Event loop (`startEventLoop`/`stopEventLoop`) | no-op (always running) | required for dispatch; `createObject`/`createProxy` auto-start it | Same calling pattern works on both; `startEventLoop` is idempotent (#114), and each native connection gets its own loop thread (#128) |
| Strict deserialization (signature mismatch rejected) | ✅ | ✅ | Same `System.Error.ENXIO` error |

✅ = identical observable behavior, asserted on both backends.

## Method calls

Sync `callMethod`, suspend `callMethodAsync`, and the callback-async variant returning
`PendingAsyncCall` (with working `cancel`, `isPending`, and release-prevents-delivery
semantics) behave identically, including parallel and bulk invocations.

- Proven by: `src/commonTest/.../integration/CommonApiIntegrationTest.kt` (the
  `typedMethodCall_*`, `suspendAsyncTypedMethodCall_*`, `callbackAsyncMethodCall_*` tests).

**Struct marshalling** of `@Serializable` types to and from *remote* peers works on both
backends, including structs nested in arrays/dicts. The JVM side decomposes structs into
wire-shaped values via `JvmValueCodec` (issue #71, fixed in PR #78).

- Proven by: `cross_test/.../integration/DbusmockTypeMatrixTest.kt` (struct round-trips
  against the foreign Python/GDBus peer) and
  `src/commonTest/.../integration/TypeMatrixRoundTripTest.kt` (own-server).

**Grouped (multi-out) replies** — methods declared with multiple out-args consumed as a
Kotlin data class with `isGroupedReturn` — deserialize correctly from remote peers on both
backends (issue #74, fixed in PR #78).

- Proven by: `cross_test/.../integration/DbusmockSecretServiceTest.kt` (`OpenSession(sv → vo)`,
  `Unlock`/`Lock (ao → ao, o)` and other real multi-out shapes).

**One JVM-only dispatch note:** when the call destination is served by the *same JVM process*,
the JVM backend takes an in-process shortcut (`JvmStaticDispatch`) instead of going through the
daemon. Results are asserted equivalent by the commonTest suites, but such calls do not exercise
the wire. Cross-process behavior *as a client* is what the `cross_test` dbusmock suites pin.

The in-process table is a same-JVM optimization, not the only serving path: an object exported
via `addVTable` is registered once and is reachable **both** in-process and over the wire, so an
*external* process calling a JVM-exported object is served by `WireServe` (#90 closed in 0.5.0) —
see "Server-side object export" below.

## Timeouts

The **per-call timeout** (`timeout = 5.seconds` inside a `callMethod`/`callMethodAsync`
block) is honored on both backends, and an expired call surfaces as a
timeout-shaped `com.monkopedia.sdbus.Error` — never a hang.

- Proven by: `src/commonTest/.../integration/FailurePathParityTest.kt`
  (`methodCallTimeout_surfacesTimeoutError`) and the `callbackAsyncMethodCall_timeout*` tests
  in `CommonApiIntegrationTest.kt`.

The **connection-level default** (`Connection.methodCallTimeout`) applies on both backends to
calls made *without* an explicit per-call timeout: on native it maps to
`sd_bus_set/get_method_call_timeout` (sd_bus resolves a 0 per-call timeout through the
connection default inside `sd_bus_call`); the JVM call paths consult the stored value the same
way (issue #80). An explicit per-call timeout always wins over the connection default; if
neither is set, each backend's own default reply timeout applies (sd-bus's 25 s default / the
JVM wire backend's own default reply timeout).

- Proven by: `FailurePathParityTest.connectionMethodCallTimeout_appliesToCallsWithoutExplicitTimeout`
  (both backends: connection default expires a slow call made with no per-call timeout, and an
  explicit per-call timeout overrides the connection default).

One honest caveat, visible in the tests:

- The exact timeout **error name** is not pinned across backends/daemons: it is
  `org.freedesktop.DBus.Error.Timeout`, `…NoReply`, or a message containing "timed out".
  `FailurePathParityTest` deliberately asserts membership in that set, not an exact name.

## Error propagation and name mapping

- A handler that throws `Error(name, message)` reaches the remote caller with the **same name
  and message verbatim** on both backends
  (`FailurePathParityTest.remoteNamedError_propagatesNameAndMessage`).
- Error replies from **foreign implementations** (arbitrary error names produced by a
  Python/GDBus peer) surface as `Error` with name and message preserved verbatim on both
  backends (`cross_test/.../DbusmockForeignErrorTest.kt`; also the `org.bluez.Error.*` and
  Secret Service error assertions in `DbusmockBluezTest.kt` / `DbusmockSecretServiceTest.kt`).
  This was a JVM bug (#72) fixed in PR #78.
- **errno-derived errors** created internally (`createError`) map to the same D-Bus error
  names and messages on both backends — e.g. `EINVAL` → `org.freedesktop.DBus.Error.InvalidArgs`,
  `ETIMEDOUT` → `…Timeout`, unmapped errnos → `System.Error.<NAME>`, unknown → `…Failed`. The JVM
  mapping is pinned test-by-test to the native `sd_bus_error_set_errno` output
  (`src/jvmTest/.../JvmCreateErrorParityTest.kt`, issue #56/#63).
- Calling through a proxy whose connection has been **released** throws
  `IllegalArgumentException("Connection has already been released")` on both backends —
  never a silent in-process dispatch
  (`FailurePathParityTest.proxyConnectionTeardown_completesCleanly_andCallAfterReleaseFails`).
- Calls to nonexistent methods or to a torn-down service surface as `Error` on both backends
  (`FailurePathParityTest`).

## Properties

`PropertiesProxy` `Get`/`Set`/`GetAll` (sync and async), read-only enforcement, and the
generated `PropertyDelegate` API (`values()`, `changes()`, and the `*OrNull` variants) behave
identically.

- Proven by: `CommonApiIntegrationTest.kt` (`propertiesProxy_*` tests) and
  `cross_test/.../DbusmockPropertiesChangedTest.kt` (changed-value payloads and
  invalidation-only signals from a foreign emitter, observed through both the raw
  callback and the reactive flows).

**`PropertiesChanged` emission** from a served object — `Object.emitPropertiesChangedSignal`
with or without an explicit property list — works on both backends with the same getter
resolution: properties whose registered getter can be read are emitted in
`changed_properties` with their *current value*; names without a readable getter fall into
`invalidated_properties`. The JVM side originally emitted invalidations only; fixed for
parity in issue #28 / PR #47.

- Proven by: `CommonApiIntegrationTest.propertiesChangedSignal_roundTripsForDeclaredProperty`
  (both backends) and `src/jvmTest/.../JvmSignalPropertyUnsupportedTest.kt` (despite its
  historical name, it now *asserts* JVM signal emission and `PropertiesChanged` emission are
  supported — it pins the closure of those former gaps).

**Generated adaptors auto-emit** `PropertiesChanged` (#115): a property annotated
`org.freedesktop.DBus.Property.EmitsChangedSignal` is bound through `Object.notifying(...)`, so
both a remote `Set` and a server-side assignment fire the signal (no-op sets are skipped) on
both backends — client property-change flows therefore update by default.

- Proven by: `cross_test/.../DbusmockAdaptorPropertiesChangedTest.propertiesChangedFires_onRemoteSet_andServerSideSet`.

> **Serving note:** `Properties.Get`/`Set`/`GetAll` *served by a JVM object* are answered over
> the owned wire connection (`WireServe`), so an external process performing `Properties.Get`
> against a JVM-hosted object is served exactly as on native (#90 closed in 0.5.0); same-JVM
> callers take the in-process shortcut. `PropertiesChanged` *emission* reaches external
> subscribers (pinned by `WireSignalEmissionExternalTest`). On native all of these are reachable
> cross-process too.

## Signals

Signal emission (`Object.emitSignal` / the `createSignal` + `send` path), subscription
(`registerSignalHandler`, `onSignal`), the reactive `signalFlow`, multi-subscriber delivery,
unsubscribe/re-subscribe lifecycle, and sender filtering behave identically.

- Proven by: `CommonApiIntegrationTest.kt` (`signalRoundTrip_*`, `signalHandler*` tests),
  `cross_test/.../DbusmockSignalTest.kt` (foreign-emitted payload shapes, `signalFlow`
  ordering, subscribe/unsubscribe lifecycle), and
  `cross_test/.../CrossModuleSignalIntegrationTest.kt`.

## ObjectManager

`Connection.addObjectManager(path)` / `Object.addObjectManager()`, `GetManagedObjects` over
the deeply nested `a{oa{sa{sv}}}` shape, `InterfacesAdded`/`InterfacesRemoved` emission and
consumption, and the `ObjectManagerProxy` reactive state flows behave identically.

- Proven by: `CommonApiIntegrationTest.interfacesAddedAndRemovedSignals_roundTripForExplicitList`,
  `cross_test/.../DbusmockObjectManagerTest.kt` (foreign-emitted lifecycle + `GetManagedObjects`
  round-trip + proxy flow convergence), and `cross_test/.../DbusmockBluezTest.kt`
  (ObjectManager discovery over the dbusmock `bluez5` template).

> **Serving note:** the verified `GetManagedObjects` round-trips above exercise sdbus-kotlin
> *as a client* against a foreign emitter, plus own-server round-trips on both backends. A JVM
> object *serving* `GetManagedObjects` to an **external** process is now answered over the wire
> (`WireServe`), reachable cross-process exactly as on native (#90 closed in 0.5.0; pinned by
> `WireServeExternalTest`); `InterfacesAdded`/`Removed` *emission* also reaches the bus. See
> "Server-side object export".

## Server-side object export

Both backends are full server backends. This was the last JVM gap (#90); it closed in 0.5.0
when the JVM target gained its own D-Bus connection (epic #93), so the matrix rows above are at
parity for serving as well as for consuming.

**Native:** `createObject(path).addVTable(...)` registers the object with sd-bus, so methods,
`org.freedesktop.DBus.Properties` (`Get`/`Set`/`GetAll`), `org.freedesktop.DBus.Introspectable`,
and `ObjectManager.GetManagedObjects` are callable by **any** peer on the bus — busctl, a native
client, a JVM client, another process.

**JVM:** `addVTable` registers the object once into the shared dispatch registries, and the
owned wire connection's serve path (`WireServe`) answers **incoming** method calls,
`Properties.Get`/`Set`/`GetAll`, `Introspect`, `Peer.Ping`/`GetMachineId`, and
`ObjectManager.GetManagedObjects` over the wire — so an external process reaches a JVM-exported
object exactly as it would a native one. Signals (incl. `PropertiesChanged`,
`InterfacesAdded`/`Removed`) are emitted as real D-Bus signals the bus routes to every
subscriber. Each incoming call runs on a bounded, deadlock-free serve worker pool (#101), off
the reader thread, so a slow or re-entrant handler cannot stall I/O. Same-JVM client→server
calls still take an in-process shortcut (`JvmStaticDispatch`) for speed, but that is an
optimization over the same registration, not a separate code path.

- Proven cross-process by: `cross_test/.../jvmTest/.../WireServeExternalTest.kt`
  (`externalBusctl_reachesServedObject` — an external `busctl` invokes a JVM-served object's
  methods, `Properties.Get`/`Set`/`GetAll`, `Introspect`, and `GetManagedObjects`) and
  `WireSignalEmissionExternalTest.kt` (`emittedSignal_reachesExternalDbusMonitor`). The
  `samples/demo-service` sample (#88) is a runnable server exercising the same path.

Both backends are now interchangeable for *serving* as well as *consuming*; the choice of
backend is purely a deployment decision (JVM runtime vs. self-contained native binary).

## Unix file descriptors

The `UnixFd` **type contract** is shared: `UnixFd(fd)`/`UnixFd(other)` duplicate the
descriptor, `UnixFd.adopt(fd)` takes ownership as-is, `release()` closes owned descriptors
and is idempotent, and copies survive release of the original.

- Proven by: `src/commonTest/.../unit/UnixFdCommonTest.kt` (runs on both backends).

Backend differences:

- **Native**: full fd semantics via POSIX `dup`/`close`. FD passing over the wire
  (signature `h`) is verified against the independent dbusmock peer, including reading real
  bytes through a received pipe fd
  (`cross_test/.../DbusmockTypeMatrixTest.unixFd_passesThroughForeignPeer_whereSupported`).
- **JVM**: duplication and closing of raw descriptors are implemented via junixsocket's
  `NativeUnixSocket` (reflection; see `JvmUnixFdSupport` in
  `src/jvmMain/.../Types.jvm.kt`). When that native library is unavailable, `UnixFd(fd)`
  does **not** duplicate (it wraps the same fd) and `release()` cannot close it —
  `src/jvmTest/.../JvmUnixFdTest.kt` gates its dup assertion on
  `supportsFdDuplicationSemantics` for exactly this reason. On the wire, `UnixFd` converts
  to/from a junixsocket `FileDescriptor` (`DBusWireConnection.kt`), but the independent-peer
  fd round-trip is skipped on JVM (test code cannot mint raw pipe fds portably under JPMS —
  see `cross_test/src/jvmTest/.../DbusmockFdSupport.jvm.kt`), so JVM wire fd passing is
  **not CI-verified**. Treat it as best-effort; prefer the native backend for fd-heavy
  protocols.

## Connection factories

There are 11 `create*Connection` entry points — 10 declared in the common API
(`src/commonMain/.../Connection.kt`) plus one declared only in the native source set
(`src/nativeMain/.../Connection.native.kt`):

| Factory | JVM | Native |
| --- | --- | --- |
| `createBusConnection()` / `(name)` | ✅ | ✅ |
| `createSystemBusConnection()` / `(name)` | ✅ | ✅ |
| `createSessionBusConnection()` / `(name)` / `(address)` | ✅ | ✅ |
| `createRemoteSystemBusConnection(host)` | ❌ native-only (not declared) | ✅ |
| `createDirectBusConnection(address)` | ✅ | ✅ |
| `createDirectBusConnection(fd: UnixFd)` | ❌ native-only | ✅ |
| `createServerBusConnection(fd: UnixFd)` | ❌ native-only | ✅ |

- The two **fd-based factories** are native-only. The JVM actuals are marked
  `@Deprecated(level = ERROR)` (`src/jvmMain/.../Connection.jvm.kt`), so calling them from
  JVM-compiled code is a **compile-time error**; if invoked anyway (e.g. reflectively), the
  JVM backend rejects the fd-based bus types (`JvmBusType.DIRECT_FD`/`SERVER_FD`, in
  `WireDbusBackend.createConnection`).
  On native they adopt the descriptor as documented in their KDoc.
- `createRemoteSystemBusConnection(host)` is **native-only and not declared in the common
  or JVM API** (it lives in `src/nativeMain/.../Connection.native.kt`). It opens the system
  bus of a remote host over ssh via sd-bus (`sd_bus_open_system_remote`, which spawns
  `ssh … systemd-stdio-bridge`); the JVM backend has no equivalent transport, and the former
  JVM declaration wrongly treated the host as a bus *address*, so it was removed
  (#82). To connect to a TCP-exposed bus on JVM, use the address-based factories instead.

**Bus-unreachable behavior:** when opening the bus fails (e.g. no
`DBUS_SESSION_BUS_ADDRESS`, or an address that points nowhere), **both backends throw
`com.monkopedia.sdbus.Error`** (issue #81; previously the JVM backend silently fell back to
an in-process stub that faked success). The exact error *name* is not pinned across backends
— native carries the errno from `sd_bus_open_*`, while the JVM backend does not expose an errno —
but the type and the throw are the contract. The in-process stub backend
(`StubJvmDbusBackend` in `src/jvmMain/.../JvmDbusBackend.kt`) still exists for unit tests
that need connection plumbing without a real bus, but it is an **explicit internal opt-in**
(tests construct it directly or mock `JvmDbusBackendProvider`); it is never selected
implicitly by any factory.

- Proven by: `src/jvmTest/.../JvmUnreachableBusTest.kt` (unreachable session/direct addresses
  throw `SdbusException`).

## Event loop semantics

The calling pattern is the same on both backends — call `startEventLoop()` after registering
objects/handlers, `stopEventLoop()` (suspend) before release — but the machinery differs:

- **Native**: `startEventLoop()` launches the sd-bus I/O loop on a **dedicated per-connection
  thread** (#128 — each connection owns its own loop thread rather than sharing a bounded
  pool, so many concurrent connections can no longer starve one another); incoming method
  calls, signals, and async replies are only dispatched while it runs. `createObject(...)` and
  `createProxy(...)` auto-start the loop (`runEventLoopThread = true` by default, #114), and a
  redundant `startEventLoop()` afterwards is **idempotent** — never a second thread.
  `stopEventLoop()` signals the loop to exit and joins it; releasing the connection also stops
  it. (`ConnectionImpl.kt`; teardown bounds pinned by `FailurePathParityTest`.)
- **JVM**: the owned wire connection runs its own reader/dispatch threads from the moment the
  connection is built, so `startEventLoop()`/`stopEventLoop()` are **no-ops** (trivially
  idempotent). Dispatch is effectively always on.

Consequence: explicit `startEventLoop()` is no longer a footgun — `createObject`/`createProxy`
auto-start it on native and it is a free no-op on JVM, while a redundant call is idempotent on
both (`EventLoopStartIdempotencyTest.createObjectAutoStartsLoop_andRepeatedStartEventLoopIsIdempotent`).
You can still call it explicitly after registering handlers on a bare connection.
`currentlyProcessedMessage` is valid inside handlers on both backends
(`CommonApiIntegrationTest.signalHandler_exposesCurrentlyProcessedMessageOnProxy`,
`propertySetter_exposesCurrentlyProcessedMessageOnObject`).

## Strict deserialization and failure paths

- **Signature mismatch is rejected, identically**: deserializing a reply whose wire signature
  does not match the expected type (e.g. reading `(ii)` from an `i` reply) throws an `Error`
  named `System.Error.ENXIO` on both backends — the JVM deserializer enforces the native
  sd-bus strictness (issue #56/#63, preserved through the #71 struct work).
  Proven by: `TypeMatrixRoundTripTest.kt` (signature-mismatch section).
- **Wrong-argument-type calls** are rejected as errors, never dispatched to the handler
  (`FailurePathParityTest.callWithWrongArgumentType_surfacesError`).
- **Released vtables** reject further method calls
  (`CommonApiIntegrationTest.releasedVTable_rejectsFurtherMethodCalls`).
- **Empty collections** (`a{sv}`, arrays, nested empties) and **unsigned types** round-trip
  with correct wire signatures on both backends
  (`src/commonTest/.../EmptyCollectionWireTest.kt`, `UnsignedRoundTripTest.kt`,
  and the dbusmock type matrix).

## JVM limitations at a glance

1. **No fd-based connections**: `createDirectBusConnection(fd)` / `createServerBusConnection(fd)`
   are compile-time errors on JVM (`@Deprecated(ERROR)` stubs).
2. **Raw-fd semantics depend on junixsocket native support**: without it, `UnixFd`
   duplication/closing degrade (no dup, no close). Wire fd passing is implemented but not
   CI-verified against an independent peer.
3. **No remote-system-bus-over-ssh**: `createRemoteSystemBusConnection(host)` is
   native-only and not declared in the JVM API (#82).
4. Historical gaps — object-side signal emission and `PropertiesChanged` emission — are
   closed and pinned by `JvmSignalPropertyUnsupportedTest`; struct marshalling, foreign error
   names, and grouped replies are closed (#71/#72/#74) and pinned by the un-gated
   `cross_test` dbusmock suites; `Connection.methodCallTimeout` application and
   throw-on-unreachable-bus are closed (#80/#81) and pinned by
   `FailurePathParityTest.connectionMethodCallTimeout_appliesToCallsWithoutExplicitTimeout`
   and `JvmUnreachableBusTest`.

The **cross-process** surface (the real consumer path, e.g. talking to BlueZ) is contract-level
parity, enforced by the commonTest and cross_test suites on every CI run — including the parity
wave that closed the error-name contract, ObjectManager/PropertiesChanged payloads, use-after-release
guards, `proxy.release()` teardown, wrong-argument-count classification, same-process standard
interfaces, `dontExpectReply`, and `addMatch` well-known-sender resolution (#141, all pinned by
cross-backend regression tests).

A few **same-process / direct-connection** edges remain JVM-specific and are documented rather than
matched (they don't affect cross-process usage):

- Two brokerless **direct** connections in one JVM share a synthetic `uniqueName` (`":jvm-wire"`),
  and objects they export at the same path can clobber each other in the local dispatch table.
- `startEventLoop()` is a no-op on JVM (the reader thread auto-starts at connect), where native
  requires it to receive — a JVM proxy can receive signals without it; native cannot.
- `Properties.GetAll` on an interface the object doesn't implement returns an empty `a{sv}` on JVM,
  where native returns an error.
- A same-process `UnixFd` argument is passed by reference (not dup'd) on the local short-circuit,
  where native dups the descriptor.
- `currentlyProcessedMessage` is not guarded against use after `release()` on JVM (native is).

These are tracked as post-1.0 items; none is on the cross-process path.
