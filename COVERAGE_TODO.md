# Coverage Improvement TODO

This backlog is intentionally ordered by priority and should be worked top-to-bottom.

## 8. JVM parity and real-bus correctness
- [x] Fix real-bus signal emission API usage on JVM (`emitSignal`, `emitPropertiesChangedSignal`, `emitInterfacesAddedSignal`, `emitInterfacesRemovedSignal`).
- [x] Fix JVM `addMatch` to use generic signal handlers (match-rule based) instead of typed `DBusSignal` registration.
- [x] Ensure same-process JVM proxy calls can resolve local object methods reliably (avoid false remote-only routing for local services).
- [x] Add JVM outbound type support for wrapped D-Bus names (`BusName`/`ServiceName`, `InterfaceName`, `MemberName`/`PropertyName`).
- [x] Add JVM inbound coercion for wrapped types in nested containers (especially `Map<ObjectPath, Map<InterfaceName, ...>>` paths used by ObjectManager).
- [x] Reconcile JVM `addObjectManager` behavior with expected API semantics (currently no-op on connection backend).
- [x] Implement JVM `org.freedesktop.DBus.ObjectManager.GetManagedObjects` for local exports (connection/object manager paths + vtable-backed properties).
- [x] Align unsupported JVM connection kinds with native error semantics (avoid silent fallback for `DIRECT_FD`/`SERVER_FD`).
- [x] Align JVM sender credential semantics with native behavior (per-message sender creds, not process snapshot).
- [x] Align JVM `UnixFd` ownership semantics with native (`dup` on copy/default constructor + close-once release on JVM when native fd ops are available).
- [x] Reconcile `dontRunEventLoopThread` behavior on JVM with native semantics (currently ignored in proxy creation path).
- [x] Align `createSystemBusConnection(name)` behavior across runtimes (native currently routes through default bus path).
- [x] Add/expand JVM integration coverage for real system/session bus paths (including `createSystemBusConnection` + ObjectManager flows).
  - [x] Added JVM session-bus coverage for distinct real connections and connection-level ObjectManager signal routing (`JvmRealBusIntegrationTest`).
  - [x] Added JVM session-bus coverage for `GetManagedObjects` returning child interface/property data (`JvmRealBusIntegrationTest`).
  - [x] Added JVM session-bus coverage for signal callback sender credential resolution (`JvmRealBusIntegrationTest`).
  - [x] Added JVM system-bus coverage for distinct real connections (`createSystemBusConnection`) with graceful skip when unavailable (`JvmRealBusIntegrationTest`).
  - [x] Added JVM system-bus ObjectManager `GetManagedObjects` coverage using unique-name routing with graceful skip when unavailable (`JvmRealBusIntegrationTest`).
- Done when:
  - [x] `:jvmTest` is green,
  - [x] `:cross_test:jvmTest` is green,
  - [x] the bluez sample can run on JVM with device discovery on hosts where BlueZ is available.

## 1. Cross-runtime Unix FD transfer scenarios
- [x] Add bidirectional direct-bus FD round-trip tests (JVM -> native and native -> JVM).
- [x] Cover FD as method input and method output (not just one direction).
- [x] Validate FD semantics via `pipe()` payload checks (write on one side, read on other side).
- [x] Verify ownership/lifecycle behavior:
  - sender closes original FD after send and receiver still reads correctly,
  - receiver release/cleanup closes owned FD exactly once.
- [x] Add negative-path tests for invalid/closed FD and assert stable error mapping.
- Done when:
  - [x] both root interop and `cross_test` contain FD interop cases,
  - [x] all FD tests pass reliably under repeated runs (`--rerun-tasks` + loop).

## 2. Build out unit test coverage for code generator
- [x] Expand parser tests for XML edge cases (missing attrs, unknown annotations, malformed signatures).
- [x] Add deterministic name-mangling tests (reserved words, collisions, casing, package mapping).
- [x] Add type-mapping tests (primitives, arrays, dicts, structs, variants, Unix FD).
- [x] Add golden-file tests for generated proxy/adaptor snippets.
- [x] Add tests for generation options toggles (proxies-only, adaptors-only, output package overrides).
- Done when:
  - key codegen paths are covered by unit tests in `codegen/src/test`,
  - golden outputs are stable and reviewed.

## 3. Build full integration tests for code generator + usage (generated code)
- [x] Add Gradle TestKit coverage for plugin wiring (`plugin` + sample build).
- [x] Generate code from fixture XML in tests, then compile and run generated usage scenarios.
- [x] Add runtime integration checks where generated proxies/adaptors call real bus peers.
- [x] Verify generated code works for JVM and native targets used in this repo.
  - Added `GeneratedCodeIntegrationTest` in `commonTest` using generated proxy/adaptor fixtures.
  - Validated on both `:jvmTest` and `:linuxX64Test`.
- Done when:
  - end-to-end generate -> compile -> run tests are automated in CI.

## 4. Richer payload interoperability (large maps/variants) across JVM/native
- [x] Add bidirectional large map payload tests (size + content fidelity checks).
- [x] Add nested variant payload tests (`Variant<Map<...>>`, map/list nesting).
- [x] Add mixed payload tests combining primitives, maps, variants, and optional values.
- [x] Assert stable serialization/deserialization and no truncation or type drift.
- Done when:
  - [x] both directions pass with large payloads in root interop tests and `cross_test`.

## 5. Create new module/home for stress testing separate from other testing
- [x] Create a dedicated `stress_test` module with isolated fixtures and Gradle tasks.
- [x] Keep stress scenarios duplicated from normal integration tests where applicable (no migration-only).
- [x] Add task-level controls for repeat count, timeout, and case filtering via system properties.
- [x] Ensure stress tasks are opt-in and do not run on normal `test`/`check`.
- Done when:
  - [x] stress tests run independently (`./gradlew :stress_test:<task>`),
  - [x] normal CI time remains unaffected.

## 6. Timeout/cancellation/error-path interop stress cases
- [x] Add repeated timeout tests for async method calls in both directions.
- [x] Add cancellation races for pending calls (cancel-before-reply, cancel-during-reply).
- [x] Add disconnect/dropped-peer scenarios while calls are in flight.
- [x] Add explicit error propagation checks (name/message parity across runtimes).
- [x] Add cleanup assertions for slot/resource release after failure paths.
- Done when:
  - stress runs show stable behavior with no hangs/crashes and consistent error contracts.

## 7. Long-run flaky/stress matrix in CI (many repeats)
- [x] Add CI jobs for cross-runtime stress loops with configurable repeat counts.
- [x] Split CI into:
  - PR-tier smoke (fast, lower repeats),
  - scheduled/nightly stress (high repeats, broader matrix).
- [x] Include matrix axes for direction (JVM->native/native->JVM), payload class, and scenario type.
- [x] Publish artifacts on failure (native logs, test XML, Gradle reports).
- [x] Add clear flake triage policy: reproduce locally, isolate introducing change, then fix.
- Done when:
  - CI produces repeatable stress signal and actionable failure artifacts.
