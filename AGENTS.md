# Repository Guidelines

`CLAUDE.md` is the authority on build, verification, and release mechanics. This file is the
contributor-facing summary; where the two disagree, `CLAUDE.md` wins and this file should be fixed.

## Project Structure & Module Organization
This repository is a Kotlin Multiplatform D-Bus client targeting **jvm + linuxX64 + linuxArm64**.
The native targets wrap `sd-bus` via cinterop; the JVM target is a pure-Kotlin implementation over
an owned junixsocket connection (since 0.5.0 — no dbus-java, no native code).

- `src/commonMain/kotlin`: shared API and serialization logic.
- `src/commonTest/kotlin`: cross-backend tests — these run on *both* backends, so this is where
  parity coverage belongs (see `docs/BACKENDS.md`).
- `src/jvmMain/kotlin`: JVM backend (wire protocol, marshaller, dispatcher).
- `src/nativeMain/kotlin` + `src/nativeInterop/cinterop`: Linux native implementation and C interop.
- `src/jvmTest/kotlin`, `src/nativeTest/kotlin/{unit,integration,mocks}`: backend-specific tests and
  test fixtures.
- `codegen/`: JVM CLI/library for XML-to-Kotlin code generation.
- `plugin/`: Gradle plugin (`com.monkopedia.sdbus.plugin`) built on top of `:codegen`.
- `compile_test/`: compile-time integration module for dependency wiring.
- `cross_test/`: cross-runtime interop suites (a client on one backend against a peer on the other,
  plus `python3-dbusmock` peers).
- `stress_test/`: long-running interop stress suites.
- `samples/bluez-scan/`, `samples/demo-service/`: example consumer projects.

## Build, Test, and Development Commands
Use the Gradle wrapper from repo root. **JDK 17+ required** (Gradle 9); the system default is often
Java 8 and will fail — set `JAVA_HOME=/usr/lib/jvm/java-17-openjdk` (or java-21).

Needs no D-Bus session bus:

- `./gradlew compileKotlinJvm compileKotlinLinuxX64 compileKotlinLinuxArm64`: compile all targets.
- `./gradlew ktlintCheck apiCheck`: the static gates CI runs. `apiCheck` is binary-compatibility
  validator; if you intentionally change public API, regenerate with `./gradlew apiDump` and commit
  the `api/*.api` diff.
- `./gradlew codegen:fatJar`: build the standalone codegen JAR used in releases.
- `./gradlew :dokkaGeneratePublicationHtml`: generate docs into `build/dokka` (this is what
  `pages.yaml` runs; the older `dokkaHtml` task still exists but is not what CI publishes).

Needs a D-Bus session bus — run under `dbus-run-session`:

- `dbus-run-session -- ./gradlew allTests test`: the library's actual test suite. This is what CI
  runs (`arm-build-test.yaml` adds `crossRuntimeInteropTests`). Both task names are needed:
  `allTests` covers the Kotlin Multiplatform modules, `test` covers the plain-JVM `:codegen` and
  `:plugin`.
- `dbus-run-session -- ./gradlew build`: full build across modules, tests included.
- `dbus-run-session -- ./gradlew licenseCheckForKotlin`: validate license headers. It deliberately
  depends on the compile/link/test tasks, so it pulls the bus-requiring suites in with it.
  (`licenseCheck` also works, but only by Gradle's abbreviation matching.)

**⚠️ Do not verify with bare `./gradlew test`.** There is no `test` task on the root project, so the
name resolves only in the two plain-JVM subprojects: the whole graph is `:codegen:test` and
`:plugin:test`. Both are pure JVM and need no bus, so the command goes **green on a machine with no
session bus while running none of the marshaller, wire-backend, sd-bus interop, or parity suites**.
Confirm any test command with `--dry-run` before trusting it.

The real suites fail loudly rather than silently when the bus is missing — measured on `main` with
`DBUS_SESSION_BUS_ADDRESS` unset, `:jvmTest` failed 150 of 331 tests and `:linuxX64Test` failed 180
of 301. A green run of those tasks is therefore real evidence; a green bare `./gradlew test` is not.
(`:jvmTest` was 135 of 331 before #227 removed the suites that returned early — and so reported a
pass — instead of failing when they could not reach a bus.)

The `samples/bluez-scan` and blue-falcon-sdbus integration suites need a real BlueZ adapter and a
BLE peripheral, so they cannot run in CI.

## Coding Style & Naming Conventions
- Follow `.editorconfig`: 4-space indentation, LF endings, max line length 100.
- Kotlin style is enforced with `ktlint` (`android_studio` profile).
- Use `UpperCamelCase` for classes, `lowerCamelCase` for functions/properties, and clear package names under `com.monkopedia.sdbus`.
- Keep public API changes intentional; this repo uses Kotlin binary compatibility validation (`apiValidation`).

## Testing Guidelines
- Place tests alongside module scope. Prefer `src/commonTest` for anything both backends must
  satisfy — that is the mechanism that makes backend parity structural rather than aspirational.
  Use `src/jvmTest` / `src/nativeTest` only for genuinely backend-specific behaviour, and
  `codegen/src/test/kotlin` / `plugin/src/test/kotlin` for the JVM tooling.
- Prefer descriptive test names that reflect behavior, e.g. `createsProxyFromXml`.
- Run `dbus-run-session -- ./gradlew allTests test` before PRs; include integration coverage when
  touching transport/interop paths.
- A test that cannot run its subject must say so. Skipping when a prerequisite is absent is fine;
  passing when it is absent is not — a suite that reports green without having exercised anything is
  worse than one that fails.
- If a native test flake appears, stop feature work immediately and investigate first (reproduce, isolate, and identify the introducing change) before continuing.
- TODO: add deterministic coverage for native `createCleaner` auto-release paths (without explicit `release()`), likely via a dedicated native test harness that can force/synchronize cleanup and assert expected unref/close side effects.

## Commit & Pull Request Guidelines
- Use Conventional Commits, scoped where it helps: `fix(native): …`, `feat(codegen): …`,
  `test(cross): …`, `docs: …`. Keep the subject short and imperative.
- PRs should include:
  - purpose and module(s) changed,
  - linked issue/release context,
  - test evidence — name the command you actually ran (e.g.
    `dbus-run-session -- ./gradlew allTests test`, `./gradlew ktlintCheck apiCheck`) rather than
    just asserting tests pass,
  - API impact notes for public-facing changes.
