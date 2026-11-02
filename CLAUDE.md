# CLAUDE.md

Guidance for agents working on sdbus-kotlin.

## What this is

A Kotlin Multiplatform D-Bus client (a port of sdbus-c++), targeting **jvm + linuxX64 + linuxArm64**. The native targets wrap `sd-bus` via cinterop; the JVM target is backed by an owned junixsocket connection with a pure-Kotlin marshaller and dispatcher (since 0.5.0 — no dbus-java, no native code). It also ships a code generator (`:codegen`, XML → Kotlin BlueZ proxies/adaptors) and a Gradle plugin (`:plugin`, id `com.monkopedia.sdbus.plugin`).

Modules (subprojects of the root build, per `settings.gradle.kts`): root (the library), `:codegen`, `:compile_test`, `:cross_test`, `:plugin`, `:stress_test`. Plus two **separate** Gradle builds that are not subprojects — `samples/bluez-scan` and `samples/demo-service` — each composing the library in with `includeBuild("../..")`.

`:compile_test` is easy to miss and is the guard that the generated code actually compiles: its source directory is a **symlink** (`compile_test/src/nativeMain/kotlin/TestFiles` → `codegen/src/test/resources`), so a linuxX64 compile of that module type-checks every checked-in codegen golden against the real library — this is what caught the malformed signal decoder in #237, which `:codegen`'s own in-process `CompilationIntegrationTest` had missed. CI covers it because the root `allTests` reaches `:compile_test:compileKotlinLinuxX64`; run it directly with `./gradlew :compile_test:compileKotlinLinuxX64`. It is deliberately excluded from `ktlintCheck` and `apiCheck` (see `build.gradle.kts`).

## Building & verifying

- **JDK 17+ required** (Gradle 9). The system default is often Java 8 and will fail — use `JAVA_HOME=/usr/lib/jvm/java-17-openjdk` (or java-21). The JVM bytecode target is pinned to 17 so published artifacts are deterministic regardless of build JDK.
- Compile: `./gradlew compileKotlinJvm compileKotlinLinuxX64 compileKotlinLinuxArm64`
- Static gates (run in CI's `static-checks` job): `./gradlew ktlintCheck apiCheck`. `apiCheck` is binary-compatibility-validator; if you intentionally change the public API, regenerate with `./gradlew apiDump` and commit the `api/*.api` diff.
- Unit/integration tests (`jvmTest`, `linuxX64Test`) need a **D-Bus session bus** — run under `dbus-run-session -- ./gradlew …`.
- The `samples/bluez-scan` and blue-falcon-sdbus integration suites need a **real BlueZ adapter + BLE peripheral** (hardware); they can't run in CI.

## Releasing

1. Bump the version everywhere it is hardcoded: `gradle.properties`, the README install snippets, `samples/bluez-scan/build.gradle.kts`, `samples/demo-service/build.gradle.kts`, and the API-stability prose in `README.md` + `dokka/moduledoc.md`. Move the `CHANGELOG.md` `[Unreleased]` heading to the new version + date and add its link ref at the bottom.
2. Create a GitHub release `vX.Y.Z` → the `publish.yaml` workflow publishes to Maven Central via vanniktech with `automaticRelease = true` (no manual Sonatype Portal step). All 5 coordinates (root, `-jvm`, `-linuxx64`, `-linuxarm64`, `-codegen`) deploy as one atomic deployment.
3. SNAPSHOT versions skip signing (for local `publishToMavenLocal` cross-repo testing); real releases are signed.

## Maintenance — keep these current when things change

- **README badges:** when the Kotlin version or build setup changes, update the badge block at the top of `README.md`.
  - The **Maven Central version** badge updates automatically (shields.io reads Central).
  - The **Kotlin version** badge is hardcoded — bump it manually when the Kotlin version changes.
  - The **Build** badge tracks `.github/workflows/arm-build-test.yaml`; keep the workflow path correct if CI is renamed.
  - Maven Central has no public *download* count, so there is no downloads badge (only the namespace owner can see download stats in the Central Portal).
- **Module list:** the `Modules:` paragraph under "What this is" must enumerate every `include(...)` in `settings.gradle.kts` plus the composite-build samples. When a module is added, removed or renamed, update it there **and** the matching list in `AGENTS.md` ("Project Structure & Module Organization") — nothing else prompts it, and `:compile_test` sat unlisted here for exactly that reason (#202).
- Generated BlueZ proxy fixtures and the BCV `api/*.api` dumps are checked in. Regenerate the codegen goldens with `./gradlew :codegen:regenerateGoldens` after an intentional generator change, and the `api/*.api` dumps with `./gradlew apiDump` when the public surface changes; review the diff and commit it. `:codegen:test` only ever *compares* the goldens — it can never write them.
