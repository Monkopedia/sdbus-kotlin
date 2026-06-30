# Test coverage baseline

A reference snapshot of how much of sdbus-kotlin's **public API surface** is exercised by its
**externally-based integration tests** — tests that drive the library through a real D-Bus session
or external process, as opposed to in-process unit/marshalling tests. Use it as a base for deciding
where new tests add real value.

Last measured: **2026-06-30** (after PRs #134/#135/#136 and the #137 directed-signal fix).

## Headline

**≈ 93% (107 / 115 curated public symbols)** are exercised by the external integration suites, and
**this coverage is cross-backend** — see [Backends](#how-this-maps-onto-the-two-backends).

## Per-area table

| Area | Covered | % | Notes |
|---|---|---|---|
| Object / adaptor (vtable, signals, object-manager) | 14/14 | 100% | `connection`, `currentlyProcessedMessage` (#134) |
| Proxy (calls, signals, property delegates) | 16/16 | 100% | `connection`, `signalFlow`, `propDelegate`, `mutableDelegate` (#134) |
| Properties (direct accessors + StandardInterfaces) | 9/9 | 100% | incl. `notifying` auto-emit (cross_test) |
| Credentials (on Message) | 7/7 | 100% | all 7 members, native + JVM (#135) |
| Standard interfaces (Peer/Properties/ObjectManager/Introspectable) | 11/11 | 100% | incl. `getManagedObjects`/`objectsFor` (cross_test) |
| Signals & VTable builders | 12/13 | 92% | `setDestination` (#134) now unicast-correct on both backends (#138); residual: bare `registerProperty` |
| Connection (lifecycle, naming, matching, timeout) | 12/13 | 92% | residual: `Connection.currentlyProcessedMessage` |
| Messages (read/write, peek, serialize) | 10/12 | 83% | `peekValueType`/`containsValueOfType` (#136); residual: `copyTo`, `createPlainMessage`/`seal` |
| Types & Names (Variant, Signature, name classes) | 11/13 | 85% | residual: `signatureOf`, `TypeSignature` accessors |
| Builders / convenience (flags, typed-method, plain message) | 5/7 | 71% | residual: `buildArgs`, `RequestNameReply.fromCode` |
| **Total** | **107/115** | **≈93%** | |

Seven of ten areas are at 92–100%.

## What the remaining ~7% is (and why it stays there)

The three sub-90 areas — Messages, Types & Names, Builders/convenience — are dominated by **pure
functions and low-level marshalling primitives**: `copyTo`, `createPlainMessage`/`seal`,
`signatureOf`, `TypeSignature` accessors, `buildArgs`, `RequestNameReply.fromCode`. These **are**
tested, but in in-process unit/parity suites (`TypesCommonTest`, `MessageApiParityCommonTest`,
`UnsignedRoundTripTest`) — they don't require a bus, so they're unit concerns by nature. Putting
them in an integration test that opens a bus connection just to call a pure function would be
theater. **For a bus-driven coverage metric, ~93% with this residual is the honest ceiling.** If a
strict per-area "≥90 everywhere" is ever wanted, the correct move is to reclassify these residuals
as unit-tier and drop them from the *external* denominator — not to write contrived integration
tests for them.

`maybeDegrouped` is intentionally excluded from the denominator: it is `internal`, exercised
indirectly by every grouped-return method call.

## How this maps onto the two backends

The library targets **jvm** (junixsocket wire backend) and **linuxX64/linuxArm64** (sd-bus via
cinterop). The coverage above is **cross-backend**, achieved three ways:

| Test source set | Runs on | What it covers |
|---|---|---|
| `src/commonTest/.../integration/` | **both** (linuxX64Test + jvmTest) | the bulk: method calls, async, signals, properties, delegates, ObjectManager, directed signals, Variant introspection. `CommonApiIntegrationTest`, `ExternalApiCoverageTest`, `MessageIntrospectionCoverageTest`, `TypeMatrixRoundTripTest`, … |
| `cross_test/src/commonTest/` (dbusmock) | **both** (jvm + linuxX64) | client-side against a mock service: ObjectManager, PropertiesChanged, signals, type matrix, foreign errors, BlueZ/SecretService shapes |
| `src/nativeTest/kotlin/integration/` | **native only** | the ported sdbus-c++ integration suite (`DBusMethodTests`, `DBusAsyncMethodsTest`, `DBusGeneralTests`, `DBusStandardInterfacesTests`), event-loop concurrency, native caller credentials |
| `src/jvmTest/.../Jvm*Test` | **JVM only** | `JvmRealBusIntegrationTest`, `JvmCredentialsTest`, plus mockk/marshaller unit tests |

So almost every covered symbol is exercised by the **same commonTest/cross_test code executing on
both backends**. The one area covered by *parallel, backend-specific* tests is **Credentials**,
because the backends genuinely differ: native resolves all seven members; the JVM backend resolves
pid/uid/euid/gid/egid/supplementary but `seLinuxContext` is host-dependent (a label under SELinux,
otherwise an `SdbusException`). `CredentialsIntegrationTest` (native) and `JvmCredentialsTest` (JVM)
assert each backend's real contract. Backend behavioural differences are pinned with `expect/actual`
capability flags in `TestBackendCapabilities.kt` (e.g. `backendDeliversDirectedSignalsUnicast`,
both `true` since #137) so a regression on one backend fails the shared test rather than silently
weakening it.

## Methodology & caveats

- **Symbol-reachability, not line/branch coverage.** A symbol counts as covered if an external test
  calls it directly or drives it through a generated proxy/adaptor at runtime.
- **Denominator is a curated judgment** distilled from both BCV dumps (`api/sdbus-kotlin.api` and
  `api/sdbus-kotlin.klib.api` — the klib dump is authoritative for the inline-reified DSL the JVM
  `.api` omits), with synthetics (value-class `*-impl`, serializer plumbing), `internal` members,
  and the `@Deprecated("Removed at 1.0")` fluent layer stripped. A stricter/looser cut moves the
  headline a couple of points; the per-area shape is stable.
- **Tooling note:** `WireDbusBackend.kt` is flagged binary/`data` by the OS, so `grep` silently
  returns no matches on it — use `awk`/`rg`/Read when auditing that file.
- **dbusmock suites can false-pass locally** without a configured dbusmock venv; trust CI / the venv
  for those rows, not a bare local green.

## Re-measuring

There is no automated API-coverage gate. To refresh this baseline: regenerate the BCV dumps if the
public surface changed (`./gradlew apiDump`), curate the denominator per the rules above, then
`rg`-check each public symbol against the external suites listed in the Backends table. Run the
suites under a session bus: `dbus-run-session -- ./gradlew :jvmTest :linuxX64Test`
(`JAVA_HOME=/usr/lib/jvm/java-17-openjdk`).
