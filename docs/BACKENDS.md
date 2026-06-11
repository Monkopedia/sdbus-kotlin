# JVM ↔ native backend parity matrix

sdbus-kotlin ships one common API with two implementations: the **native** backend
(`linuxX64`/`linuxArm64`, sd-bus via libsystemd) and the **JVM** backend (dbus-java,
pure-Java transport). This document is the contract for what behaves identically on both
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
| Connection-level `Connection.methodCallTimeout` | ⚠️ stored but not applied | ✅ applied | Known divergence, see below |
| Error propagation: named D-Bus errors round-trip name + message verbatim | ✅ | ✅ | Incl. foreign (non-sdbus) peers; JVM fixed in #72 |
| errno → D-Bus error-name mapping for locally created errors | ✅ | ✅ | JVM pinned to native output |
| Call-after-release fails with `IllegalArgumentException("Connection has already been released")` | ✅ | ✅ | |
| Properties: `Get`/`Set`/`GetAll`, property delegates (`values()`/`changes()`) | ✅ | ✅ | |
| `PropertiesChanged` emission incl. getter resolution | ✅ | ✅ | JVM fixed in #28 |
| Signals: emission, subscription, `signalFlow` | ✅ | ✅ | |
| ObjectManager: `GetManagedObjects`, `InterfacesAdded`/`Removed`, `ObjectManagerProxy` flows | ✅ | ✅ | |
| `UnixFd` type semantics (dup constructor vs `adopt`, `release` closes) | ✅* | ✅ | *JVM dup needs junixsocket native support |
| Unix FD passing over the wire | ⚠️ untested | ✅ | JVM has the conversion path but no independent-peer test |
| Connection factories (11 total) | 9 of 11 | 11 of 11 | fd-based factories are native-only |
| Behavior when the bus is unreachable | ⚠️ silent in-process stub | throws `Error` | Known divergence, see below |
| Event loop (`startEventLoop`/`stopEventLoop`) | no-op (always running) | required for dispatch | Same calling pattern works on both |
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
the JVM backend dispatches in-process (`JvmStaticDispatch`/`LocalJvmServiceRegistry`) instead
of going through the daemon. Results are asserted equivalent by the commonTest suites, but
such calls do not exercise the wire. Cross-process behavior is what the `cross_test` dbusmock
suites pin.

## Timeouts

The **per-call timeout** (`timeout = 5.seconds` inside a `callMethod`/`callMethodAsync`
block) is honored on both backends, and an expired call surfaces as a
timeout-shaped `com.monkopedia.sdbus.Error` — never a hang.

- Proven by: `src/commonTest/.../integration/FailurePathParityTest.kt`
  (`methodCallTimeout_surfacesTimeoutError`) and the `callbackAsyncMethodCall_timeout*` tests
  in `CommonApiIntegrationTest.kt`.

Two honest caveats, both visible in the tests:

- The exact timeout **error name** is not pinned across backends/daemons: it is
  `org.freedesktop.DBus.Error.Timeout`, `…NoReply`, or a message containing "timed out".
  `FailurePathParityTest` deliberately asserts membership in that set, not an exact name.
- **Known divergence — `Connection.methodCallTimeout`:** on native this property maps to
  `sd_bus_set/get_method_call_timeout` and applies as the default for calls without an
  explicit timeout (`ConnectionImpl.kt`). On JVM the setter stores the value and the getter
  returns it, but no call path consults it (`PureJavaDbusConnection` in
  `PureJavaDbusBackend.kt`) — calls without a per-call timeout use dbus-java's own default
  reply timeout instead. Use per-call timeouts for portable behavior.

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
  to/from dbus-java's `FileDescriptor` (`PureJavaDbusBackend.kt`), but the independent-peer
  fd round-trip is skipped on JVM (test code cannot mint raw pipe fds portably under JPMS —
  see `cross_test/src/jvmTest/.../DbusmockFdSupport.jvm.kt`), so JVM wire fd passing is
  **not CI-verified**. Treat it as best-effort; prefer the native backend for fd-heavy
  protocols.

## Connection factories

There are 11 `create*Connection` entry points (`src/commonMain/.../Connection.kt`):

| Factory | JVM | Native |
| --- | --- | --- |
| `createBusConnection()` / `(name)` | ✅ | ✅ |
| `createSystemBusConnection()` / `(name)` | ✅ | ✅ |
| `createSessionBusConnection()` / `(name)` / `(address)` | ✅ | ✅ |
| `createRemoteSystemBusConnection(host)` | ⚠️ | ✅ |
| `createDirectBusConnection(address)` | ✅ | ✅ |
| `createDirectBusConnection(fd: UnixFd)` | ❌ native-only | ✅ |
| `createServerBusConnection(fd: UnixFd)` | ❌ native-only | ✅ |

- The two **fd-based factories** are native-only. The JVM actuals are marked
  `@Deprecated(level = ERROR)` (`src/jvmMain/.../Connection.jvm.kt`), so calling them from
  JVM-compiled code is a **compile-time error**; if invoked anyway (e.g. reflectively), the
  JVM backend throws (`PureJavaDbusBackend.createConnection` rejects `DIRECT_FD`/`SERVER_FD`).
  On native they adopt the descriptor as documented in their KDoc.
- `createRemoteSystemBusConnection(host)` exists on both, but the argument is interpreted
  differently: native uses sd-bus's ssh transport (`sd_bus_open_system_remote`, takes a
  hostname), while the JVM backend passes the string as a dbus-java bus *address* (e.g.
  `tcp:host=…,port=…`). Neither path is covered by CI tests; do not assume portability.

**Known divergence — bus-unreachable behavior:** when opening the bus fails (e.g. no
`DBUS_SESSION_BUS_ADDRESS`), the native backend throws `com.monkopedia.sdbus.Error`. The JVM
backend instead **silently falls back to an in-process stub backend**
(`StubJvmDbusBackend` in `src/jvmMain/.../JvmDbusBackend.kt`): the returned connection only
reaches objects/proxies created inside the same process (in-process match bus), and
bus-level operations are no-ops. Only `createDirectBusConnection(address)` propagates the
connect failure as an `Error` on JVM. If your JVM application must talk to a real bus,
verify reachability explicitly (e.g. check `connection.uniqueName` is not `:jvm-stub`).

## Event loop semantics

The calling pattern is the same on both backends — call `startEventLoop()` after registering
objects/handlers, `stopEventLoop()` (suspend) before release — but the machinery differs:

- **Native**: `startEventLoop()` launches the sd-bus I/O loop on an internally managed
  thread; incoming method calls, signals, and async replies are only dispatched while it
  runs. `stopEventLoop()` signals the loop to exit and joins it. Releasing the connection
  also stops the loop. (`ConnectionImpl.kt`; teardown bounds pinned by
  `FailurePathParityTest`.)
- **JVM**: dbus-java runs its own reader/dispatch threads from the moment the connection is
  built, so `startEventLoop()`/`stopEventLoop()` are **no-ops** (`PureJavaDbusConnection`).
  Dispatch is effectively always on.

Consequence: code that forgets `startEventLoop()` appears to work on JVM and silently
receives nothing on native. Always call `startEventLoop()` — it is free on JVM and required
on native. `currentlyProcessedMessage` is valid inside handlers on both backends
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
3. **`Connection.methodCallTimeout` is not applied** to calls — use per-call timeouts.
4. **Silent stub fallback when the bus is unreachable** instead of throwing.
5. **`createRemoteSystemBusConnection(host)` interprets its argument differently** (bus
   address, not ssh host).
6. Historical gaps — object-side signal emission and `PropertiesChanged` emission — are
   closed and pinned by `JvmSignalPropertyUnsupportedTest`; struct marshalling, foreign error
   names, and grouped replies are closed (#71/#72/#74) and pinned by the un-gated
   `cross_test` dbusmock suites.

Everything not listed above is contract-level parity, enforced by the commonTest and
cross_test suites on every CI run.
